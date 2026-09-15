import SwiftUI

struct HelpView: View {
    var body: some View {
        List {
            Section("最快上手") {
                StepRow(index: 1, title: "标定奶瓶", detail: "给奶瓶倒入一个已知奶量（例如 120ml），放进画面，点「识别并记录」。换 1~2 个不同奶量再记录一次，至少 2 个点。")
                StepRow(index: 2, title: "固定位置", detail: "把手机放在支架上，或者每次都用同一个姿势、同一个距离。奶瓶每次放在同一个点上。")
                StepRow(index: 3, title: "日常使用", detail: "打开 App 对准奶瓶，画面上的黄线就是液面位置，下面的大数字就是奶量。稳定后点「保存记录」。")
                StepRow(index: 4, title: "看统计", detail: "在「统计」里切换日 / 周 / 月，看折线图了解奶量变化。")
            }

            Section("为什么需要标定？") {
                Text("每个人手机离奶瓶的远近、角度都不一样，同样的液面高度对应的毫升数也不同。标定就是用几个已知奶量，让 App 学会「这个高度 = 多少毫升」。标定好之后，只要保持手机和奶瓶的相对位置不变，读数就会一直准。")
                    .font(.footnote)
            }

            Section("让读数更准的小技巧") {
                BulletRow(text: "手机尽量固定：用支架、靠墙、或者每次在同一个高度拍摄。")
                BulletRow(text: "画面里让瓶身占中间，背景尽量简单（纯色墙面最好）。")
                BulletRow(text: "光线均匀。逆光或强烈反光会影响识别，可以开闪光灯当补光灯。")
                BulletRow(text: "奶瓶放正，不要倾斜。")
                BulletRow(text: "如果识别总是不准，把标定里的「取样区域」虚线框收窄到刚好罩住瓶身。")
                BulletRow(text: "数值不合适时，保存前直接改：点「保存记录」后可以滑动调整到实际奶量。")
            }

            Section("常见问题") {
                FAQRow(question: "一定要联网吗？", answer: "不需要。识别、统计全部在手机上完成，App 没有任何联网功能。")
                FAQRow(question: "能识别两种不同的奶瓶吗？", answer: "可以。在设置里给每个奶瓶分别标定，使用时切换当前奶瓶即可。")
                FAQRow(question: "换了手机位置怎么办？", answer: "重新标定一次，或者在统计里用「编辑」把记录改成实际奶量。")
                FAQRow(question: "识别结果是 0 或者 --？", answer: "说明画面里没有找到液面。检查：奶瓶是否在虚线框内、光线是否足够、瓶子里的奶是否太少（低于标定范围）。")
                FAQRow(question: "数据会丢吗？", answer: "数据保存在本机。删除 App 会一起删掉，建议偶尔用设置里的「导出全部记录」备份一份。")
            }
        }
        .navigationTitle("使用说明")
    }
}

private struct StepRow: View {
    let index: Int
    let title: String
    let detail: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(index)")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 22, height: 22)
                .background(Color.accentColor)
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(detail).font(.footnote).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.vertical, 2)
    }
}

private struct BulletRow: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "circle.fill")
                .font(.system(size: 5))
                .foregroundStyle(Color.accentColor)
                .padding(.top, 6)
            Text(text)
                .font(.footnote)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 1)
    }
}

private struct FAQRow: View {
    let question: String
    let answer: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(question).font(.subheadline.weight(.semibold))
            Text(answer).font(.footnote).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 2)
    }
}