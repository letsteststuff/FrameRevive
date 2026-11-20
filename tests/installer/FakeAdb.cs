using System;
using System.IO;
using System.Linq;

// Standalone native process so stderr and exit codes cross the same boundary as adb.exe.
internal static class FakeAdb
{
    private static int Fail(string message, int code)
    {
        Console.Error.WriteLine(message);
        return code;
    }

    public static int Main(string[] args)
    {
        string root = Environment.GetEnvironmentVariable("FRAMEREVIVE_TEST_STATE");
        string scenario = Environment.GetEnvironmentVariable("FRAMEREVIVE_TEST_SCENARIO");
        if (String.IsNullOrEmpty(root) || !Directory.Exists(root))
            return Fail("Fake ADB requires an isolated test state directory.", 99);

        File.AppendAllText(Path.Combine(root, "calls.log"), String.Join("\t", args) + Environment.NewLine);
        string target = null;
        if (args.Length >= 2 && args[0] == "-s")
        {
            target = args[1];
            args = args.Skip(2).ToArray();
        }
        string command = String.Join(" ", args);
        if (command == "devices")
        {
            Console.WriteLine("List of devices attached");
            if (scenario == "no-device") return 0;
            Console.WriteLine("TEST-FRAME\t" + (scenario == "unauthorized" ? "unauthorized" : "device"));
            if (scenario.StartsWith("multiple-devices")) Console.WriteLine("OTHER-FRAME\tdevice");
            return 0;
        }
        if (target != null && target != "TEST-FRAME") return Fail("Unexpected target serial.", 99);
        if (command == "shell getprop ro.product.model") { Console.WriteLine("Lenovo CD-3L501F"); return 0; }
        if (command == "shell getprop ro.build.version.release")
        {
            Console.WriteLine(scenario == "wrong-android" ? "11" : "10"); return 0;
        }
        if (command == "shell pm path com.walnut.bluetooth")
        {
            if (scenario != "wrong-frame") Console.WriteLine("package:/system/app/FakeLenovo.apk");
            return 0;
        }
        if (command == "shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        {
            string home = Path.Combine(root, "home.txt");
            Console.WriteLine(File.Exists(home) ? File.ReadAllText(home) : "com.walnut.bluetooth/.SplashActivity");
            return 0;
        }
        if (args.Length > 0 && args[0] == "install")
        {
            if (args.Length != 3 || args[1] != "-r" || !File.Exists(args[2]))
                return Fail("Expected install -r with the supplied APK path.", 99);
            if (scenario == "install-failure") return Fail("INSTALL_FAILED_TEST_FIXTURE", 1);
            File.WriteAllText(Path.Combine(root, "installed"), "true");
            Console.WriteLine("Success"); return 0;
        }
        if (command == "shell am start -n com.framerevive.app/.MainActivity")
        {
            string counter = Path.Combine(root, "launch-count.txt");
            int count = File.Exists(counter) ? Int32.Parse(File.ReadAllText(counter)) + 1 : 1;
            File.WriteAllText(counter, count.ToString());
            if (scenario == "launch-failure" || (scenario == "final-launch-failure" && count == 2))
                return Fail("Error: Activity class does not exist (test fixture).", 1);
            Console.WriteLine("Starting: Intent { cmp=com.framerevive.app/.MainActivity }");
            if (scenario == "launch-warnings")
                Console.Error.WriteLine("Warning: Activity not started, intent has been delivered to currently running top-most instance.");
            return 0;
        }
        if (command == "shell am start -a android.settings.WIFI_SETTINGS")
        {
            Console.WriteLine("Starting: Intent { act=android.settings.WIFI_SETTINGS }"); return 0;
        }
        if (command == "shell run-as com.framerevive.app cat shared_prefs/frame-settings.xml")
        {
            string counter = Path.Combine(root, "settings-read-count.txt");
            int count = File.Exists(counter) ? Int32.Parse(File.ReadAllText(counter)) + 1 : 1;
            File.WriteAllText(counter, count.ToString());
            if (!File.Exists(Path.Combine(root, "installed")) || scenario == "initialization-failure" ||
                (scenario == "delayed-initialization" && count <= 2))
                return Fail("cat: frame-settings.xml: No such file or directory (test fixture)", 1);
            Console.WriteLine("<map><string name=\"managementKey\">11111111111111111111111111111111</string>" +
                "<boolean name=\"httpsManagement\" value=\"true\" />" +
                "<string name=\"localHostname\">framerevive-11223344.local</string></map>");
            return 0;
        }
        if (command == "shell run-as com.framerevive.app base64 files/management-cert.der")
        {
            Console.WriteLine(Convert.ToBase64String(File.ReadAllBytes(Path.Combine(root, "certificate.cer"))));
            return 0;
        }
        if (command == "shell dumpsys device_policy")
        {
            if (File.Exists(Path.Combine(root, "admin"))) Console.WriteLine("com.framerevive.app/.FrameAdminReceiver");
            return 0;
        }
        if (command == "shell dpm set-active-admin com.framerevive.app/.FrameAdminReceiver")
        {
            File.WriteAllText(Path.Combine(root, "admin"), "true"); return 0;
        }
        if (command == "shell cmd package set-home-activity com.framerevive.app/.MainActivity")
        {
            File.WriteAllText(Path.Combine(root, "home.txt"), "com.framerevive.app/.MainActivity"); return 0;
        }
        if (command == "shell am force-stop com.walnut.bluetooth" ||
            command == "shell pm disable-user --user 0 com.walnut.bluetooth") return 0;
        if (command == "shell ip -f inet addr show wlan0")
        {
            if (scenario != "no-wifi") Console.WriteLine("inet 192.0.2.42/24 scope global wlan0");
            return 0;
        }
        if (command == "forward tcp:18765 tcp:8765") return 0;
        return Fail("Unexpected ADB command: " + command, 99);
    }
}
