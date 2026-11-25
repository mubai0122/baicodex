/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-24
 * Repository: https://github.com/mubai0122/baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * 赞赏入口与赞赏码弹窗：二维码不放进 res（会被别人换掉/换包），
 * 以 AES-256-CBC 密文存 assets/reward_qr.bin，密钥与 IV 内置在代码里，运行时解出来再显示。
 */

package b.ai.ui.screen.settings.about
import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.ui.screen.settings.ui.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.kit.*

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.App
import b.ai.R
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.tap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256 密钥（base64，随机生成）：只用来把赞赏码从 assets 里解出来，防的是「换图占便宜」，不是防逆向 */
internal const val QR_KEY_B64 = "878xDn6MD7VBvwUKgh5C8+/F+Fij5ePTqYFBFAsDn0U="

/** AES IV（16 字节，hex）：与上面的 key 成对内置，改任一个都会解不出来 */
internal const val QR_IV_HEX = "2f0b0c03e40ac2429b99e6774a3dcbdf"

/** hex 串转字节数组：给 IV 用 */
internal fun hexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
//十六进制解码

/**
 * 解出赞赏码：assets/reward_qr.bin 是 AES-256-CBC 密文，解出来后直接进 Bitmap，不落盘。
 * 失败返回 null，界面上退化成一句提示，不影响其它功能。
 */
fun rewardBitmap(): Bitmap? = runCatching {
    val enc = App.self.assets.open("reward_qr.bin").use { it.readBytes() }
    val key = Base64.decode(QR_KEY_B64, Base64.NO_WRAP)
    val iv = hexToBytes(QR_IV_HEX)
    val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
    c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    val png = c.doFinal(enc)
    BitmapFactory.decodeByteArray(png, 0, png.size)
}.getOrNull()
//解出赞赏码位图

/** 关于页里的一行入口：赞赏作者 */
fun LazyListScope.rewardEntry(onOpen: () -> Unit) {
    item {
        Spacer(Modifier.height(14.dp))
        CardBox {
            Row(
                Modifier.fillMaxWidth().tap { onOpen() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Favorite, null, tint = AccentBlue, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.rw_entry_t),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        stringResource(R.string.rw_entry_d),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Text("›", fontSize = 18.sp, color = TextSecondary)
            }
        }
    }
} //赞赏入口行

/** 赞赏弹窗：一句话说明成本与免费，然后是赞赏码，末尾一句感谢 */
@Composable
fun RewardDialog(onClose: () -> Unit) {
    val bmp = remember { rewardBitmap() }
    ScrimCard(onDismiss = onClose) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Favorite, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.rw_entry_t),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.rw_body),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Start,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(14.dp))
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.rw_qr_cd),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.rw_qr_scan), fontSize = 10.sp, color = TextSecondary)
            } else {
                Text(stringResource(R.string.rw_qr_fail), fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                GhostButton(stringResource(R.string.rw_close), modifier = Modifier.height(38.dp)) { onClose() }
            }
        }
    }
} //赞赏弹窗