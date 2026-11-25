/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:23
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.settings.core
import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.ui.screen.settings.ui.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.kit.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.ui.glass.GlassPerf
import b.ai.ui.glass.liquidBg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.tap

/**
 * 分类页：底栏点进来看到的第一屏，只列子页入口。
 * 行尾回显交给各子页自己给（S2 起接 summary），S1 阶段先只显示副标题
 */
fun LazyListScope.seCatHome(catLabel: Int, sub: String, pages: List<SePage>, onOpen: (SePage) -> Unit) {
    item {
        //分类名的标题固定画出来：原来只在 sub 非空时画，导致分类页卡片上面光秃秃没有标题
        SectionTitle(stringResource(catLabel), sub.ifBlank { null })
        CardBox {
            pages.forEachIndexed { i, p ->
                if (i > 0) Spacer(Modifier.height(2.dp))
                SeRow(p) { GlassPerf.switched(); onOpen(p) }
            }
        }
    }
} //分类页

/** 分类页上的一行：标题 + 副标题 + 右箭头 */
@Composable
fun SeRow(p: SePage, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(p.titleRes), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (p.subRes != 0) {
                Text(stringResource(p.subRes), fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            }
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(2.dp))
    }
} //子页入口行

/**定位胶囊：从搜索结果跳进子页时钉在页首，说清「你要找的是哪一项」，点一下收起 */
@Composable
fun LocatePill(label: String, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .liquidBg(RoundedCornerShape(50), tint = AccentBlueSoft, gate = 0.dp)
            .tap { onClose() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "定位：" + label,
            fontSize = 11.sp,
            color = AccentBlue,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.se_061),
            tint = AccentBlue,
            modifier = Modifier.size(13.dp)
        )
    }
} //定位胶囊

/** 二级页顶部的返回行：跟系统手势并存的点击退路 */
@Composable
fun SeBackBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
            .tap { onBack() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.se_059),
            tint = AccentBlue,
            modifier = Modifier.size(18.dp)
        )
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
    }
} //子页返回条