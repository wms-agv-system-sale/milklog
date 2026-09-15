package com.example.milklog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 使用说明。 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "bar") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onBack() }) { Text("‹ 返回") }
                Spacer(Modifier.weight(1f))
                Text("使用说明", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
        }

        item(key = "steps") {
            AppCard {
                Column {
                    Text("最快上手", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    StepRow(1, "标定奶瓶", "给奶瓶倒入一个已知奶量（例如 120ml），放进画面，点「识别并记录」。换 1~2 个不同奶量再记录一次，至少 2 个点。")
                    StepRow(2, "固定位置", "把手机放在支架上，或者每次都用同一个姿势、同一个距离。奶瓶每次放在同一个点上。")
                    StepRow(3, "日常使用", "打开 App 对准奶瓶，画面上的黄线就是液面位置，下面的大数字就是奶量。稳定后点「保存记录」。")
                    StepRow(4, "看统计", "在「统计」里切换日 / 周 / 月，看折线图了解奶量变化。")
                }
            }
        }

        item(key = "why") {
            AppCard {
                Column {
                    Text("为什么需要标定？", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "每个人手机离奶瓶的远近、角度都不一样，同样的液面高度对应的毫升数也不同。标定就是用几个已知奶量，让 App 学会「这个高度等于多少毫升」。标定好之后，只要保持手机和奶瓶的相对位置不变，读数就会一直准。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "tips") {
            AppCard {
                Column {
                    Text("让读数更准的小技巧", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    BulletRow("手机尽量固定：用支架、靠墙，或者每次在同一个高度拍摄。")
                    BulletRow("画面里让瓶身占中间，背景尽量简单（纯色墙面最好）。")
                    BulletRow("光线均匀。逆光或强烈反光会影响识别，可以开闪光灯当补光灯。")
                    BulletRow("奶瓶放正，不要倾斜。")
                    BulletRow("如果识别总是不准，把标定里的「取样区域」虚线框收窄到刚好罩住瓶身。")
                    BulletRow("数值不合适时，保存前直接改：点「保存记录」后可以滑动调整到实际奶量。")
                }
            }
        }

        item(key = "faq") {
            AppCard {
                Column {
                    Text("常见问题", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    FaqRow("一定要联网吗？", "不需要。识别、统计全部在手机上完成，这个 App 没有任何联网功能。")
                    FaqRow("能识别两种不同的奶瓶吗？", "可以。在设置里给每个奶瓶分别标定，使用时切换当前奶瓶即可。")
                    FaqRow("换了手机位置怎么办？", "重新标定一次，或者在统计里点开某条记录，把数值改成实际奶量。")
                    FaqRow("识别结果是 -- ？", "说明画面里没有找到液面。检查：奶瓶是否在虚线框内、光线是否足够、奶量是否太少（低于标定范围）。")
                    FaqRow("数据会丢吗？", "数据保存在本机。卸载 App 会一起删掉，建议偶尔在设置里用「导出全部记录」备份一份。")
                }
            }
        }
    }
}

@Composable
private fun StepRow(index: Int, title: String, detail: String) {
    Row(modifier = Modifier.padding(vertical = 5.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(index.toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FaqRow(question: String, answer: String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(question, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(answer, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
