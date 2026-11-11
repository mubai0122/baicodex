#pragma once
#include <cstdint>
//本地模型推理：llama.cpp 之上的句柄式薄封装，模型与推理上下文分开持有，输出按 token 流式回调。
//句柄是自增 id 不是裸指针，Kotlin 传错句柄只返回失败，不会把进程搞崩。
extern "C" {
//加载 GGUF：nThreads<=0 用默认，nGpuLayers=0 纯 CPU。失败返回 0，原因看 bai_llm_err()
uint64_t bai_llm_load(const char *path, int nThreads, int nGpuLayers);
void bai_llm_free(uint64_t h);

//元信息：每行 key=value，含 desc / n_layer / n_ctx_train / n_params / size / chat（0=模型没带模板）
const char *bai_llm_meta(uint64_t h);

//最近一次失败原因，只在当前调用线程内有效
const char *bai_llm_err();

//推理上下文：ctxLen<=0 用模型训练时的上下文，太小会被抬到 512
uint64_t bai_llm_ctx_new(uint64_t h, int ctxLen);
void bai_llm_ctx_free(uint64_t c);

//采样参数：temp<=0 走贪心，repeatPenalty<=0 视为 1.0 关闭惩罚
void bai_llm_set_sampling(uint64_t c, float temp, float topP, int topK, float repeatPenalty, int seed);

//生成：每轮先清 KV 再整段喂 prompt，逐 token 回调文本片段
//返回 >=0 实际生成 token 数；-1 参数错，-2 提示超上下文，-3 被取消，-4 推理失败
int bai_llm_run(uint64_t c, const char *prompt, int maxTokens, void (*onToken)(void *, const char *), void *ud);

//取消正在跑的生成：只置标志，循环里下一 token 前生效
void bai_llm_cancel(uint64_t c);

//按模型自带 chat template 拼 prompt：roles/texts 成对给，返回写入字节数（cap 不够时返回所需长度，负数=没有模板）
int bai_llm_chat(uint64_t h, const char *const *roles, const char *const *texts, int n, int addAss, char *buf,
                 int cap);
} //本地模型 C 接口
