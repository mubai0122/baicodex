#include "core/feature/llm/llm.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <random>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>
#include <sys/stat.h>
#include "llama.h"
namespace {
struct Llm {
    llama_model *m = nullptr;
    int threads = 4;
    std::string meta; //加载时算好的元信息，每行 key=value
    std::string tmpl; //模型自带 chat template，空表示没带
};
struct Ctx {
    std::shared_ptr<Llm> owner;
    llama_context *c = nullptr;
    llama_sampler *s = nullptr;
    int ctxLen = 4096;
    int nBatch = 512;
    float temp = 0.8f;
    float topP = 0.9f;
    float rep = 1.1f;
    int topK = 40;
    int seed = -1;
    std::atomic<bool> cancel{false};
};
std::mutex gMu;
uint64_t gSeq = 1;
std::unordered_map<uint64_t, std::shared_ptr<Llm>> gModels;
std::unordered_map<uint64_t, std::shared_ptr<Ctx>> gCtxs;
thread_local std::string gErr; //错误原因：JNI 在同一线程里紧接着读，thread_local 够用

std::shared_ptr<Llm> findLlm(uint64_t h) {
    std::lock_guard<std::mutex> lk(gMu);
    auto it = gModels.find(h);
    return it == gModels.end() ? nullptr : it->second;
} //按句柄取模型
std::shared_ptr<Ctx> findCtx(uint64_t h) {
    std::lock_guard<std::mutex> lk(gMu);
    auto it = gCtxs.find(h);
    return it == gCtxs.end() ? nullptr : it->second;
} //按句柄取上下文

//采样链：惩罚 → top-k → top-p → 温度 → 取词，temp<=0 走贪心
void rebuildSampler(Ctx &x) {
    if (x.s) {
        llama_sampler_free(x.s);
        x.s = nullptr;
    }
    auto cp = llama_sampler_chain_default_params();
    cp.no_perf = true;
    x.s = llama_sampler_chain_init(cp);
    const auto *vocab = llama_model_get_vocab(x.owner->m);
    const int nVocab = llama_vocab_n_tokens(vocab);
    if (x.rep > 0.0f && x.rep != 1.0f) {
        llama_sampler_chain_add(x.s, llama_sampler_init_penalties(nVocab, 64, x.rep, 0.0f, 0.0f));
    }
    if (x.temp <= 0.0f) {
        llama_sampler_chain_add(x.s, llama_sampler_init_greedy());
        return;
    }
    if (x.topK > 0) llama_sampler_chain_add(x.s, llama_sampler_init_top_k(x.topK));
    if (x.topP > 0.0f && x.topP < 1.0f) llama_sampler_chain_add(x.s, llama_sampler_init_top_p(x.topP, 1));
    llama_sampler_chain_add(x.s, llama_sampler_init_temp(x.temp));
    uint32_t sd = x.seed >= 0 ? static_cast<uint32_t>(x.seed)
                              : static_cast<uint32_t>(std::random_device{}());
    llama_sampler_chain_add(x.s, llama_sampler_init_dist(sd));
} //重建采样链

int defaultThreads() {
    unsigned hw = std::thread::hardware_concurrency();
    if (hw == 0) hw = 4;
    int n = static_cast<int>(hw) - 2; //留两个核给 UI 与解码线程，避免整机卡死
    if (n < 2) n = 2;
    if (n > 6) n = 6;
    return n;
} //默认线程数:当前线程数
} //namespace

extern "C" {
uint64_t bai_llm_load(const char *path, int nThreads, int nGpuLayers) {
    if (!path || !*path) {
        gErr = "模型路径为空";
        return 0;
    }
    struct stat st{};
    if (stat(path, &st) != 0 || st.st_size <= 0) {
        gErr = std::string("模型文件不存在:") + path;
        return 0;
    }
    llama_backend_init();
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = nGpuLayers > 0 ? nGpuLayers : 0;
    mp.load_mode = LLAMA_LOAD_MODE_MMAP; //mmap 加载：不占双份内存，模型多大就映射多大
    llama_model *m = llama_model_load_from_file(path, mp);
    if (!m) {
        gErr = std::string("模型加载失败(GGUF 损坏或版本不兼容):") + path;
        return 0;
    }
    auto lm = std::make_shared<Llm>();
    lm->m = m;
    lm->threads = nThreads > 0 ? nThreads : defaultThreads();
    if (const char *t = llama_model_chat_template(m, nullptr)) lm->tmpl = t;
    char desc[256] = {0};
    llama_model_desc(m, desc, sizeof(desc));
    char buf[512];
    snprintf(buf, sizeof(buf), "desc=%s\nn_layer=%d\nn_ctx_train=%d\nn_params=%lld\nsize=%lld\nchat=%d\nthreads=%d",
             desc, llama_model_n_layer(m), llama_model_n_ctx_train(m),
             static_cast<long long>(llama_model_n_params(m)), static_cast<long long>(st.st_size),
             lm->tmpl.empty() ? 0 : 1, lm->threads);
    lm->meta = buf;
    std::lock_guard<std::mutex> lk(gMu);
    uint64_t id = gSeq++;
    gModels[id] = lm;
    gErr.clear();
    return id;
} //加载 GGUF 模型
void bai_llm_free(uint64_t h) {
    std::shared_ptr<Llm> lm;
    {
        std::lock_guard<std::mutex> lk(gMu);
        auto it = gModels.find(h);
        if (it == gModels.end()) return;
        lm = it->second;
        gModels.erase(it);
    }
    //上下文还活着就先断掉，避免用法释后指针
    std::vector<std::shared_ptr<Ctx>> dead;
    {
        std::lock_guard<std::mutex> lk(gMu);
        for (auto it = gCtxs.begin(); it != gCtxs.end();) {
            if (it->second->owner == lm) {
                dead.push_back(it->second);
                it = gCtxs.erase(it);
            } else {
                ++it;
            }
        }
    }
    for (auto &x : dead) {
        if (x->s) llama_sampler_free(x->s);
        if (x->c) llama_free(x->c);
        x->s = nullptr;
        x->c = nullptr;
    }
    llama_model_free(lm->m);
    lm->m = nullptr;
} //释放模型
const char *bai_llm_meta(uint64_t h) {
    auto lm = findLlm(h);
    if (!lm) {
        gErr = "模型句柄无效";
        return "";
    }
    return lm->meta.c_str();
} //模型元信息
const char *bai_llm_err() { return gErr.c_str(); } //最近一次失败原因
uint64_t bai_llm_ctx_new(uint64_t h, int ctxLen) {
    auto lm = findLlm(h);
    if (!lm) {
        gErr = "模型句柄无效";
        return 0;
    }
    const int train = llama_model_n_ctx_train(lm->m);
    int want = ctxLen > 0 ? ctxLen : train;
    if (want < 512) want = 512;
    if (train > 0 && want > train) want = train;
    auto cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(want);
    cp.n_batch = static_cast<uint32_t>(want < 512 ? want : 512);
    cp.n_ubatch = cp.n_batch;
    cp.n_threads = lm->threads;
    cp.n_threads_batch = lm->threads;
    cp.no_perf = true;
    llama_context *c = llama_init_from_model(lm->m, cp);
    if (!c) {
        gErr = "上下文创建失败(内存不足时常见)";
        return 0;
    }
    auto x = std::make_shared<Ctx>();
    x->owner = lm;
    x->c = c;
    x->ctxLen = want;
    x->nBatch = static_cast<int>(cp.n_batch);
    rebuildSampler(*x);
    std::lock_guard<std::mutex> lk(gMu);
    uint64_t id = gSeq++;
    gCtxs[id] = x;
    gErr.clear();
    return id;
} //建推理上下文
void bai_llm_ctx_free(uint64_t h) {
    std::shared_ptr<Ctx> x;
    {
        std::lock_guard<std::mutex> lk(gMu);
        auto it = gCtxs.find(h);
        if (it == gCtxs.end()) return;
        x = it->second;
        gCtxs.erase(it);
    }
    if (x->s) llama_sampler_free(x->s);
    if (x->c) llama_free(x->c);
    x->s = nullptr;
    x->c = nullptr;
} //释放上下文
void bai_llm_set_sampling(uint64_t h, float temp, float topP, int topK, float repeatPenalty, int seed) {
    auto x = findCtx(h);
    if (!x) {
        gErr = "上下文句柄无效";
        return;
    }
    x->temp = temp;
    x->topP = topP;
    x->topK = topK;
    x->rep = repeatPenalty > 0.0f ? repeatPenalty : 1.0f;
    x->seed = seed;
    rebuildSampler(*x);
} //设置采样参数
int bai_llm_run(uint64_t h, const char *prompt, int maxTokens, void (*onToken)(void *, const char *), void *ud) {
    auto x = findCtx(h);
    if (!x || !x->c || !prompt) {
        gErr = "上下文句柄无效";
        return -1;
    }
    if (maxTokens <= 0) maxTokens = 256;
    x->cancel.store(false);
    const auto *vocab = llama_model_get_vocab(x->owner->m);
    const auto texto = std::string(prompt);
    const int need = -llama_tokenize(vocab, texto.data(), static_cast<int32_t>(texto.size()), nullptr, 0, true, true);
    if (need <= 0) {
        gErr = "分词失败(空提示或模板不匹配)";
        return -1;
    }
    std::vector<llama_token> toks(static_cast<size_t>(need));
    const int got = llama_tokenize(vocab, texto.data(), static_cast<int32_t>(texto.size()), toks.data(), need, true,
                                   true);
    if (got <= 0) {
        gErr = "分词失败";
        return -1;
    }
    toks.resize(static_cast<size_t>(got));
    if (static_cast<int>(toks.size()) + 8 >= x->ctxLen) {
        gErr = "提示已超出上下文(" + std::to_string(toks.size()) + "/" + std::to_string(x->ctxLen) + ")";
        return -2;
    }
    llama_memory_clear(llama_get_memory(x->c), true); //每轮重算，先清 KV
    struct llama_batch b = llama_batch_init(static_cast<int32_t>(x->nBatch), 0, 1);
    int rc = 0;
    int nGen = 0;
    size_t pos = 0;
    while (pos < toks.size()) {
        const int n = static_cast<int>(std::min(static_cast<size_t>(x->nBatch), toks.size() - pos));
        b.n_tokens = n;
        for (int i = 0; i < n; ++i) {
            b.token[i] = toks[pos + static_cast<size_t>(i)];
            b.pos[i] = static_cast<llama_pos>(pos + static_cast<size_t>(i));
            b.n_seq_id[i] = 1;
            b.seq_id[i][0] = 0;
            b.logits[i] = (pos + static_cast<size_t>(i) == toks.size() - 1) ? 1 : 0;
        }
        if (llama_decode(x->c, b) != 0) {
            rc = -4;
            gErr = "提示解码失败";
            break;
        }
        pos += static_cast<size_t>(n);
    }
    const int nPastBase = static_cast<int>(toks.size());
    while (rc == 0 && nGen < maxTokens) {
        if (x->cancel.load()) {
            rc = -3;
            gErr = "已取消";
            break;
        }
        const llama_token id = llama_sampler_sample(x->s, x->c, -1);
        llama_sampler_accept(x->s, id);
        if (llama_vocab_is_eog(vocab, id)) break;
        char piece[512];
        const int k = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, false);
        if (k > 0 && onToken) onToken(ud, std::string(piece, static_cast<size_t>(k)).c_str());
        ++nGen;
        b.n_tokens = 1;
        b.token[0] = id;
        b.pos[0] = static_cast<llama_pos>(nPastBase + nGen - 1);
        b.n_seq_id[0] = 1;
        b.seq_id[0][0] = 0;
        b.logits[0] = 1;
        if (llama_decode(x->c, b) != 0) {
            rc = -4;
            gErr = "生成解码失败";
            break;
        }
    }
    llama_batch_free(b);
    if (rc == 0) gErr.clear();
    return rc == 0 ? nGen : rc;
} //跑一轮生成
void bai_llm_cancel(uint64_t h) {
    auto x = findCtx(h);
    if (x) x->cancel.store(true);
} //请求取消生成
int bai_llm_chat(uint64_t h, const char *const *roles, const char *const *texts, int n, int addAss, char *buf,
                 int cap) {
    auto lm = findLlm(h);
    if (!lm) {
        gErr = "模型句柄无效";
        return -1;
    }
    if (!roles || !texts || n <= 0) {
        gErr = "对话参数为空";
        return -1;
    }
    std::vector<llama_chat_message> msgs;
    msgs.reserve(static_cast<size_t>(n));
    for (int i = 0; i < n; ++i) {
        msgs.push_back({roles[i], texts[i]});
    }
    const char *tmpl = lm->tmpl.empty() ? "chatml" : lm->tmpl.c_str();
    const int need = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), addAss != 0, buf, cap);
    if (need < 0) {
        gErr = std::string("模型不支持该 chat template:") + tmpl;
        return -1;
    }
    if (need > cap) return need; //调用方按返回长度重新分配
    return need;
} //按 chat template 拼 prompt
} //extern "C"