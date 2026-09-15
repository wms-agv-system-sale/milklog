import SwiftUI

@main
struct MilkLogApp: App {
    @StateObject private var store = AppStore()
    @StateObject private var engine = MeasurementEngine()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(engine)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active {
                        engine.start()
                    } else if phase == .background {
                        engine.stop()
                    }
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var engine: MeasurementEngine
    @State private var tab: Int = 0

    var body: some View {
        TabView(selection: $tab) {
            CameraScreen()
                .tabItem { Label("记录", systemImage: "camera.viewfinder") }
                .tag(0)

            StatsView()
                .tabItem { Label("统计", systemImage: "chart.xyaxis.line") }
                .tag(1)

            SettingsView()
                .tabItem { Label("设置", systemImage: "gearshape") }
                .tag(2)
        }
        .onAppear { engine.start() }
    }
}