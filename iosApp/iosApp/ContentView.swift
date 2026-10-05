import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Values come from Config.xcconfig via Info.plist. Only public client config lives
        // here; paid AI keys stay on the server.
        let info = Bundle.main.infoDictionary ?? [:]
        PlatformConfig.shared.gatewayUrl = info["FitCalGatewayUrl"] as? String ?? ""
        PlatformConfig.shared.supabaseUrl = info["FitCalSupabaseUrl"] as? String ?? ""
        PlatformConfig.shared.supabaseAnonKey = info["FitCalSupabaseAnonKey"] as? String ?? ""
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea()
    }
}
