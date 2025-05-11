import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Client1 {
    private static final String SERVER_IP = "192.168.244.128"; // Replace with your server's actual IP if different
    private static final int PORT = 2500;
    private static final String CLIENT_NAME = "Client1";
    private static Socket socket;
    private static PrintWriter out;
    private static volatile boolean running = true; // volatile ensures visibility across threads

    public static void main(String[] args) {
        // Using a ScheduledExecutorService for periodic tasks and a general thread pool
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2); // Pool size for scheduled + maybe other async tasks

        try {
            System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Attempting to connect to server " + SERVER_IP + ":" + PORT + "...");
            socket = new Socket(SERVER_IP, PORT);
            out = new PrintWriter(socket.getOutputStream(), true); // Enable auto-flush
            System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Connected successfully.");

            // Start a dedicated thread for listening to server responses
            new Thread(() -> listenForServerResponses(socket)).start();
            System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Server response listener started.");

            // Run local scripts asynchronously (assuming they exist in the same directory)
            runAsyncScript("ssh_config.sh");
            runAsyncScript("fix_perms.sh");
            // Run login_audit.sh in the background, redirecting output
            runBackgroundScript("login_audit.sh");
            System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Initial local scripts launched.");


            // Schedule task requests every 5 seconds (no initial delay)
            // --- CHANGE MADE HERE ---
            System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Scheduling periodic tasks every 5 seconds.");
            scheduler.scheduleAtFixedRate(() ->
                sendTaskRequest("2001", "1"), 0, 5, TimeUnit.MINUTES); // Changed from MINUTES to SECONDS
            scheduler.scheduleAtFixedRate(() ->
                sendTaskRequest("2003", "1"), 0, 5, TimeUnit.MINUTES); // Changed from MINUTES to SECONDS

            // Start thread to handle manual user commands from the console
            handleManualCommands();
            System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Ready for manual commands (type 'exit' to quit).");


        } catch (IOException e) {
            logError("Connection failed: " + e.getMessage() + ". Ensure the server is running and reachable at " + SERVER_IP + ":" + PORT);
            running = false; // Stop other threads if connection fails initially
        } finally {
            // This finally block might be reached quickly if connection fails,
            // or only when 'exit' is typed or another fatal error occurs.
            // Wait for the scheduler to finish tasks if needed (optional, depends on desired exit behavior)
             if (!running) { // Only shutdown scheduler if we are intending to exit
                 System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Shutting down scheduler...");
                 scheduler.shutdown();
                 try {
                     // Wait a bit for scheduled tasks to finish
                     if (!scheduler.awaitTermination(5, TimeUnit.MINUTES)) {
                         scheduler.shutdownNow(); // Force shutdown if tasks don't complete
                     }
                 } catch (InterruptedException ie) {
                     scheduler.shutdownNow();
                     Thread.currentThread().interrupt();
                 }
                 System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Scheduler shut down.");
                 cleanup(); // Close socket and streams
                 System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Exiting.");
             }
        }
    }

    // Listens for incoming messages from the server on a dedicated thread
    private static void listenForServerResponses(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String response;
            // Keep listening as long as the client is running and the server sends messages
            while (running && (response = in.readLine()) != null) {
                formatResponse(response);
            }
        } catch (IOException e) {
            // Log error only if the client wasn't intentionally stopped
            if (running) {
                logError("Lost connection to server or read error: " + e.getMessage());
                running = false; // Assume connection is lost, trigger shutdown
                // Consider attempting reconnection here or notifying the user more explicitly
            }
        } finally {
             // If the loop exits (e.g., server disconnects), ensure cleanup happens
             if (running) { // If exited unexpectedly
                 System.out.println("["+ getCurrentTimestamp() + "] CLIENT1 - Server connection closed unexpectedly.");
                 running = false; // Trigger shutdown sequence in main thread's finally block or command handler
             } else {
                 System.out.println("["+ getCurrentTimestamp() + "] CLIENT1 - Server response listener stopped.");
             }
        }
    }

    // Formats and prints responses received from the server
    private static void formatResponse(String response) {
        String[] parts = response.split(";", 3); // Split into STATUS;TIMESTAMP;MESSAGE
        if (parts.length < 3) {
            // Print raw response if format is unexpected
            System.out.println("\n[Server] " + response);
            return;
        }
        // Print formatted response
        System.out.println("\n[" + getCurrentTimestamp() + "] === Server Response ===");
        System.out.println(" Status: " + parts[0]);
        System.out.println(" Server Time: " + parts[1]);
        System.out.println(" Message:");
        // Print multi-line messages correctly
        String[] messageLines = parts[2].split("\n");
        for (String line : messageLines) {
            System.out.println("   " + line); // Indent message lines for clarity
        }
        System.out.println("========================");
         // Prompt user again after showing server response
         System.out.print("\nEnter command (QUEUE_STATUS/CANCEL_TASK;ID/TASK_HISTORY/exit): ");
    }

    // Runs a local script asynchronously in a new thread
    private static void runAsyncScript(String scriptName) {
        new Thread(() -> {
            logInfo("Starting local script: " + scriptName);
            try {
                ProcessBuilder pb = new ProcessBuilder("./" + scriptName);
                pb.redirectErrorStream(true); // Merge stdout and stderr of the script
                Process process = pb.start();

                // Log output from the script (optional, can be noisy)
                logScriptOutput(scriptName, process);

                int exitCode = process.waitFor(); // Wait for script completion
                if (exitCode == 0) {
                    logInfo(scriptName + " completed successfully.");
                } else {
                    logError(scriptName + " finished with exit code: " + exitCode);
                }
            } catch (IOException e) {
                logError("Failed to run " + scriptName + " (IOException): " + e.getMessage());
            } catch (InterruptedException e) {
                logError("Execution of " + scriptName + " interrupted: " + e.getMessage());
                Thread.currentThread().interrupt(); // Restore interrupted status
            } catch (Exception e) {
                logError("Failed to run " + scriptName + " (General Exception): " + e.getMessage());
            }
        }, "ScriptRunner-" + scriptName).start(); // Give the thread a descriptive name
    }

    // Runs a script in the background using nohup, redirecting output
    private static void runBackgroundScript(String scriptName) {
         logInfo("Starting background script: " + scriptName + " with output to audit.log/script_errors.log");
        try {
            // Using nohup ensures the script keeps running even if the client terminal closes (in some environments)
            // Redirect standard output to audit.log (append)
            // Redirect standard error to script_errors.log (append)
            new ProcessBuilder("nohup", "./" + scriptName)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(new File("audit.log")))
                .redirectError(ProcessBuilder.Redirect.appendTo(new File("script_errors.log")))
                .start();
             logInfo("Background script " + scriptName + " launched.");
        } catch (IOException e) {
            logError("Failed to start background script " + scriptName + ": " + e.getMessage());
        }
    }

    // Sends a task request to the server (synchronized to prevent potential issues with 'out' if accessed by multiple threads)
    private static synchronized void sendTaskRequest(String serviceNumber, String priority) {
         if (!running || out == null || socket.isClosed()) {
             logError("Cannot send task request - connection not active.");
             return;
         }
        String request = "REQUEST_TASK;" + serviceNumber + ";" + CLIENT_NAME + ";" + priority;
        logInfo("Sending request: " + request);
        out.println(request);
        if (out.checkError()) { // Check if PrintWriter encountered an error
            logError("Error sending task request. Server might be disconnected.");
            running = false; // Assume connection is lost
        }
    }

    // Handles manual commands entered by the user in the console
    private static void handleManualCommands() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running) {
                System.out.print("\nEnter command (QUEUE_STATUS/CANCEL_TASK;ID/TASK_HISTORY/exit): ");
                if (!scanner.hasNextLine()) { // Handle end of input stream
                     running = false;
                     break;
                }
                String command = scanner.nextLine().trim();

                 if (!running) break; // Check running flag again after blocking read

                if (command.equalsIgnoreCase("exit")) {
                    running = false; // Signal other threads to stop
                    // Cleanup is handled in the main thread's finally block
                    break; // Exit command loop
                } else if (command.isEmpty()) {
                     continue; // Ignore empty input
                } else if (out == null || !running || socket.isClosed()) {
                     logError("Cannot send command - connection not active.");
                     continue;
                 }


                // Send the command to the server
                logInfo("Sending command: " + command);
                out.println(command);
                 if (out.checkError()) {
                     logError("Error sending command. Server might be disconnected.");
                     running = false; // Assume connection is lost
                 }
            }
            scanner.close();
            System.out.println("["+ getCurrentTimestamp() + "] CLIENT1 - Manual command listener stopped.");
             // Ensure cleanup is triggered if exit wasn't the cause
             if (running){
                running = false;
             }
             // Initiate cleanup if not already done
             cleanup();

        }, "ManualCommandHandler").start();
    }

    // Logs output from a running script process (run in its own thread)
    private static void logScriptOutput(String scriptName, Process process) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                     // Log script output with script name prefix (optional)
                     // System.out.println("[" + getCurrentTimestamp() + "] [" + scriptName + "] " + line);
                     // Suppressed to match desired output from description, but kept for debugging option
                }
            } catch (IOException e) {
                logError("Error reading output from " + scriptName + ": " + e.getMessage());
            }
        }, "OutputLogger-" + scriptName).start();
    }

    // Cleans up resources like socket and PrintWriter
    private static synchronized void cleanup() {
        // Ensure cleanup happens only once and when intended
        System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Starting cleanup...");
        running = false; // Explicitly set running to false
        try {
            if (out != null) {
                out.close(); // Close PrintWriter first
                out = null; // Help garbage collection
                 System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - PrintWriter closed.");
            }
            if (socket != null && !socket.isClosed()) {
                socket.close(); // Close socket
                 System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Socket closed.");
            }
             socket = null; // Help garbage collection
        } catch (IOException e) {
            logError("Error during cleanup: " + e.getMessage());
        }
         System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 - Cleanup finished.");
    }

    // Utility method for logging informational messages
    private static void logInfo(String message) {
        System.out.println("[" + getCurrentTimestamp() + "] CLIENT1 INFO - " + message);
    }


    // Utility method for logging error messages
    private static void logError(String message) {
        System.err.println("[" + getCurrentTimestamp() + "] CLIENT1 ERROR - " + message);
    }

     // Utility method to get current timestamp as string
    private static String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")); // Added milliseconds
    }
}

