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
                    StepRow(1, "对准奶瓶", "把奶瓶放正，让瓶身上印的刻度数字落在画面中间的方框里，光线均匀。")
                    StepRow(2, "按一下快门", "点中间圆形的「拍照」按钮。App 只会识别这一张照片（比实时画面准），一两秒出结果。")
                    StepRow(3, "核对后保存", "照片上绿色虚线是识别到的液面、蓝线是读到的刻度位置。数值不对就用 -10 / +10 调整，或者点「重拍」重来。确认后点「保存记录」。")
                    StepRow(4, "看统计", "在「统计」里切换日 / 周 / 月，看折线图了解奶量变化。")
                }
            }
        }

        item(key = "why") {
            AppCard {
                Column {
                    Text("识别是怎么做到的？", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "不需要任何标定。刻度本来就印在奶瓶上，App 直接读这些数字，再加上牛奶液面的位置，就能算出奶量。所以换奶瓶、换位置都不用重新设置，拍照时让数字看得清就行。",
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
                    BulletRow("按快门之前手停稳一下，照片越清楚识别越准。")
                    BulletRow("瓶身边缘尽量竖直，不要倾斜；背景简单一点（纯色墙面最好）。")
                    BulletRow("光线均匀，别让灯光在瓶身上形成大片反光，可以用「补光」。")
                    BulletRow("数字只读出一半时，稍微退后一点，让整排刻度都进画面再拍。")
                    BulletRow("数值不合适就直接改：保存前用 -10 / +10，或者保存后进记录里改。")
                }
            }
        }

        item(key = "faq") {
            AppCard {
                Column {
                    Text("常见问题", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    FaqRow("一定要联网吗？", "不需要。识别模型就在 App 里，识别和统计全部在手机上完成，这个 App 没有任何联网功能。")
                    FaqRow("换奶瓶要重新设置吗？", "不用。只要瓶身上有刻度数字，直接拍就行。")
                    FaqRow("识别不出来怎么办？", "点「重拍」再试一次：让刻度数字更清楚、瓶身放在画面中间、光线均匀。实在不行就点「手动输入」自己填。")
                    FaqRow("读数差了 10ml 怎么办？", "用 -10 / +10 调一下再保存；也可以保存后进记录里改。")
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
