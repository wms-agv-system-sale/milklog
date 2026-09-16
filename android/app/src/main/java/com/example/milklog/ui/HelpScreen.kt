package com.example.milklog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
                    Text("三个步骤", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    StepRow(1, "记一次喂奶", "在「记录」页，点最下面那个「＋ 记一次喂奶」。")
                    StepRow(2, "填奶量", "输入奶量，或者点下面的常用数值、拖滑块来调，都是 10 ml 一档。时间默认是现在，可以在同一页改成别的时间。")
                    StepRow(3, "保存", "点「保存」，这条就记下了。记错了在列表里点它，就能改数值或者删掉。")
                }
            }
        }

        item(key = "stats") {
            AppCard {
                Column {
                    Text("看统计", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "在「统计」页切换日 / 周 / 月：日视图是当天的每一次喂奶折线，周和月是这段时间里每一次喂奶的折线，再加上每天总奶量的折线，能看出奶量的变化趋势。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "tips") {
            AppCard {
                Column {
                    Text("小技巧", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    BulletRow("喂完马上记，顺手也就几秒。")
                    BulletRow("想记昨天的奶，进「手动输入」的页面把时间改成昨天就行。")
                    BulletRow("备注里可以写点别的，比如「喝了一半」「配方奶」。")
                    BulletRow("统计页可以切换日 / 周 / 月，看奶量的变化趋势。")
                }
            }
        }

        item(key = "faq") {
            AppCard {
                Column {
                    Text("常见问题", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    FaqRow("一定要联网吗？", "不需要，这个 App 没有任何联网功能。")
                    FaqRow("换了手机会丢吗？", "会。数据只存在这台手机上，建议偶尔在设置里用「导出全部记录」备份一份。")
                    FaqRow("卸载 App 会怎样？", "所有记录会一起删掉，卸载前记得先导出。")
                    FaqRow("能记多胞胎吗？", "目前只有一份记录。需要分开记的话告诉开发者，可以加。")
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
