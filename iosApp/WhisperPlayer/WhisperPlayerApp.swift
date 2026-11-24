import SwiftUI
import WhisperPlayerUI

@main
struct WhisperPlayerIOSApp: App {
    var body: some Scene {
        WindowGroup {
            WhisperPlayerRootView()
                .ignoresSafeArea()
        }
    }
}

struct WhisperPlayerRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
