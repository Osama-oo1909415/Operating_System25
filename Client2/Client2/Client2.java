import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Client2 {
    private static final String SERVER_IP = "192.168.244.128";
    private static final int PORT = 2500;
    private static final String CLIENT_NAME = "Client2";
    private static volatile boolean running = true;

    public static void main(String[] args) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        
        try (Socket socket = new Socket(SERVER_IP, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Start async response listener
            new Thread(() -> listenForResponses(in)).start();

            // Run local scripts asynchronously
            runAsyncScript("resource_report.sh");
            runAsyncScript("quota_check.sh");

            // Schedule staggered task requests
            scheduler.scheduleAtFixedRate(() -> 
                sendRequest(out, "2004", "3"), 0, 5, TimeUnit.MINUTES);  // File audit (Low)
            scheduler.scheduleAtFixedRate(() -> 
                sendRequest(out, "2005", "1"), 2, 5, TimeUnit.MINUTES);  // MySQL login (High)

            // Handle manual commands
            handleManualInput(out);

            // Keep main thread alive
            while(running) {
                Thread.sleep(1000);
            }

        } catch (IOException | InterruptedException e) {
            logError("Connection error: " + e.getMessage());
        } finally {
            scheduler.shutdown();
            running = false;
        }
    }

    private static void listenForResponses(BufferedReader in) {
        try {
            while (running) {
                String response = in.readLine();
                if (response == null) break;
                formatAndPrint(response);
            }
        } catch (IOException e) {
            if (running) logError("Response error: " + e.getMessage());
        }
    }

    private static void handleManualInput(PrintWriter out) {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running) {
                printCommandMenu();
                String cmd = scanner.nextLine().trim().toLowerCase();
                
                if (handleExitCommand(cmd)) break;
                
                String serverCommand = processCommand(cmd, scanner);
                if (serverCommand != null) {
                    out.println(serverCommand);
                }
            }
            scanner.close();
        }).start();
    }

    private static void printCommandMenu() {
        System.out.println("\n=== Client2 Controls ===");
        System.out.println("[1] View Task Queue (QUEUE_STATUS)");
        System.out.println("[2] Cancel Task (CANCEL_TASK;ID)");
        System.out.println("[3] View History (TASK_HISTORY)");
        System.out.println("[4] Exit");
        System.out.print("Select option: ");
    }

    private static boolean handleExitCommand(String cmd) {
        if (cmd.equals("4") || cmd.equals("exit")) {
            running = false;
            return true;
        }
        return false;
    }

    private static String processCommand(String cmd, Scanner scanner) {
        switch (cmd) {
            case "1":
                return "QUEUE_STATUS";
                
            case "2":
                System.out.print("Enter TaskID to cancel: ");
                String taskId = scanner.nextLine().trim();
                if (taskId.matches("\\d+")) {
                    return "CANCEL_TASK;" + taskId;
                }
                logError("Invalid TaskID format");
                return null;
                
            case "3":
                return "TASK_HISTORY";
                
            default:
                return cmd; // Allow direct command input
        }
    }

    private static void formatAndPrint(String response) {
        String[] parts = response.split(";", 3);
        if (parts.length < 3) {
            System.out.println("[Server] " + response);
            return;
        }

        String status = parts[0];
        String timestamp = parts[1];
        String message = parts[2];

        switch (status) {
            case "QUEUE_STATUS":
                System.out.println("\n=== Current Task Queue ===");
                System.out.println(message.replace("\n", "\n• "));
                break;
                
            case "TASK_HISTORY":
                System.out.println("\n=== Task Execution History ===");
                System.out.println(message.replace("\n", "\n• "));
                break;
                
            case "EXECUTING":
                System.out.printf("\n[%s] Task %s started execution\n", 
                    timestamp, extractTaskId(message));
                break;
                
            case "COMPLETED":
                System.out.printf("\n[%s] Task %s completed successfully\n", 
                    timestamp, extractTaskId(message));
                break;
                
            default:
                System.out.printf("[%s] %s: %s\n", timestamp, status, message);
        }
    }

    private static String extractTaskId(String message) {
        try {
            return message.split("=")[1].split(" ")[0];
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private static void runAsyncScript(String scriptName) {
        new Thread(() -> {
            try {
                Process process = new ProcessBuilder("./" + scriptName).start();
                logScriptOutput(scriptName, process);
                int exitCode = process.waitFor();
                log(scriptName + " exited with code: " + exitCode);
            } catch (Exception e) {
                logError("Script failed: " + scriptName + " - " + e.getMessage());
            }
        }).start();
    }

    private static void logScriptOutput(String scriptName, Process process) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log(scriptName + ": " + line);
                }
            } catch (IOException e) {
                logError("Output read error: " + e.getMessage());
            }
        }).start();
    }

    private static synchronized void sendRequest(PrintWriter out, String service, String priority) {
        String request = String.format("REQUEST_TASK;%s;%s;%s", service, CLIENT_NAME, priority);
        out.println(request);
        log("Submitted request: " + service + " (Priority: " + priority + ")");
    }

    private static void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + timestamp + "] CLIENT2 - " + message);
    }

    private static void logError(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.err.println("[" + timestamp + "] CLIENT2 ERROR - " + message);
    }
}

