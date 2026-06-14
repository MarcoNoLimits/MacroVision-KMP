import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let openRouterApiKey = Bundle.main.infoDictionary?["OpenRouterApiKey"] as? String ?? ""
        let geminiApiKey = Bundle.main.infoDictionary?["GeminiApiKey"] as? String ?? ""
        let groqApiKey = Bundle.main.infoDictionary?["GroqApiKey"] as? String ?? ""
        PlatformConfig.shared.openRouterApiKey = openRouterApiKey
        PlatformConfig.shared.geminiApiKey = geminiApiKey
        PlatformConfig.shared.groqApiKey = groqApiKey
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
