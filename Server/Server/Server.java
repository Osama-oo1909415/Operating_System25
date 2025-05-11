import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.text.SimpleDateFormat;

public class Server {
    private static final int PORT = 2500;
    private static final PriorityBlockingQueue<Task> taskQueue = new PriorityBlockingQueue<>();
    private static final Map<String, Boolean> runningScripts = new ConcurrentHashMap<>();
    private static final Map<String, Long> clientLastRequest = new ConcurrentHashMap<>();
    private static final List<TaskLog> taskHistory = Collections.synchronizedList(new ArrayList<>());
    private static final List<PrintWriter> clientWriters = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger taskIdCounter = new AtomicInteger(100);
    private static final ExecutorService taskExecutor = Executors.newCachedThreadPool();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"));
        serverSocket.setReuseAddress(true);
        System.out.println(getCurrentTimestamp() + " - Server started on port " + PORT);

        new Thread(Server::processTasks).start();

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new ClientHandler(clientSocket).start();
        }
    }

    static class Task implements Comparable<Task> {
        final int id;
        final int serviceNumber;
        final String clientName;
        final int priority;
        final long timestamp;
        final String scriptName;

        public Task(int serviceNumber, String clientName, int priority, String scriptName) {
            this.id = taskIdCounter.incrementAndGet();
            this.serviceNumber = serviceNumber;
            this.clientName = clientName;
            this.priority = priority;
            this.scriptName = scriptName;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public int compareTo(Task other) {
            if (this.priority != other.priority)
                return Integer.compare(this.priority, other.priority);
            return Long.compare(this.timestamp, other.timestamp);
        }
    }

    static class TaskLog {
        final int id;
        final String scriptName;
        final String clientName;
        final String status;
        final String timestamp;

        public TaskLog(int id, String scriptName, String clientName, String status) {
            this.id = id;
            this.scriptName = scriptName;
            this.clientName = clientName;
            this.status = status;
            this.timestamp = getCurrentTimestamp();
        }
    }

    static class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            String clientIP = socket.getInetAddress().getHostAddress();
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                clientWriters.add(out);

                System.out.println(getCurrentTimestamp() + " - Connection from " + clientIP);

                int validationResult = validateClient();
                System.out.println(getCurrentTimestamp() + " - Network.sh validation result for "
                                  + clientIP + ": " + (validationResult == 0 ? "Success" : "Failed"));

                if (validationResult != 0) {
                    sendRejected("Client validation failed (Code: " + validationResult + ")");
                    closeResources();
                    return;
                }

                System.out.println(getCurrentTimestamp() + " - Client authenticated: " + clientIP);

                String request;
                while ((request = in.readLine()) != null) {
                    System.out.println(getCurrentTimestamp() + " - Received request from " + clientIP + ": " + request);
                    handleClientRequest(request);
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    System.err.println(getCurrentTimestamp() + " - Client connection error with " + clientIP + ": " + e.getMessage());
                }
            } finally {
                closeResources();
                System.out.println(getCurrentTimestamp() + " - Connection closed: " + clientIP);
            }
        }

        private void closeResources() {
            if (out != null) {
                clientWriters.remove(out);
            }
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println(getCurrentTimestamp() + " - Error closing socket: " + e.getMessage());
            }
        }

        private int validateClient() {
            try {
                Process process = new ProcessBuilder("./Network.sh").start();
                return process.waitFor();
            } catch (IOException | InterruptedException e) {
                System.err.println(getCurrentTimestamp() + " - Client validation script error: " + e.getMessage());
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return -1;
            }
        }

        private void handleClientRequest(String request) {
            if (request.startsWith("REQUEST_TASK")) {
                handleTaskRequest(request);
            } else if (request.equals("QUEUE_STATUS")) {
                sendQueueStatus();
            } else if (request.startsWith("CANCEL_TASK")) {
                cancelTask(request);
            } else if (request.equals("TASK_HISTORY")) {
                sendTaskHistory();
            } else {
                sendRejected("Invalid command: " + request);
            }
        }

        private void handleTaskRequest(String request) {
            String[] parts = request.split(";");
            try {
                if (parts.length < 4) throw new IllegalArgumentException("Invalid REQUEST_TASK format.");

                int serviceNumber = Integer.parseInt(parts[1]);
                String clientName = parts[2];
                int priority = Integer.parseInt(parts[3]);
                String scriptName;

                if (serviceNumber == 2005) {
                    if (parts.length != 5) {
                        sendRejected("For service 2005, format: REQUEST_TASK;2005;ClientName;Priority;UserName");
                        return;
                    }
                    scriptName = "MySQL_login_" + parts[4] + ".sh";
                } else {
                    scriptName = getScriptName(serviceNumber);
                    if (scriptName == null) {
                        sendRejected("Invalid service number: " + serviceNumber);
                        return;
                    }
                }

                if (isRateLimited(clientName)) {
                    sendRejected("Rate limit exceeded for client: " + clientName);
                    return;
                }

                Task task = new Task(serviceNumber, clientName, priority, scriptName);
                taskQueue.put(task);
                clientLastRequest.put(clientName, System.currentTimeMillis());

                System.out.println(getCurrentTimestamp() + " - [QUEUED] TaskID:" + task.id
                                  + " | Client: " + clientName + " | Script: " + scriptName
                                  + " | Priority: " + priority);

                sendResponse("Task queued with ID " + task.id);
                logTask(task.id, scriptName, clientName, "QUEUED");

            } catch (NumberFormatException e) {
                sendRejected("Invalid number format in request. ServiceNumber and Priority must be integers.");
            } catch (IllegalArgumentException e) {
                sendRejected(e.getMessage());
            }
        }

        private boolean isRateLimited(String clientName) {
            long now = System.currentTimeMillis();
            Long last = clientLastRequest.get(clientName);
            return last != null && (now - last) < 300_000;
        }

        private String getScriptName(int serviceNumber) {
            switch (serviceNumber) {
                case 2001: return "user_setup.sh";
                case 2002: return "dir_perms.sh";
                case 2003: return "system_monitor.sh";
                case 2004: return "file_audit.sh";
                default:   return null;
            }
        }

        private void sendQueueStatus() {
            StringBuilder sb = new StringBuilder();
            sb.append("Pending Tasks:");
            List<Task> snapshot = new ArrayList<>(taskQueue);
            Collections.sort(snapshot);
            if (snapshot.isEmpty()) {
                sb.append("\nQueue is empty.");
            } else {
                int idx = 1;
                for (Task t : snapshot) {
                    sb.append("\n").append(idx++).append(". TaskID=")
                      .append(t.id).append(", Script=").append(t.scriptName)
                      .append(", Priority=").append(t.priority)
                      .append(", Client=").append(t.clientName)
                      .append(", QueuedAt=").append(getCurrentTimestamp(t.timestamp));
                }
            }
            sendResponse(sb.toString());
        }

        private void cancelTask(String request) {
            String[] parts = request.split(";");
            if (parts.length != 2) {
                sendRejected("Invalid CANCEL_TASK format. Expected: CANCEL_TASK;TaskID");
                return;
            }
            try {
                int targetId = Integer.parseInt(parts[1]);
                boolean removed = false;
                Iterator<Task> it = taskQueue.iterator();
                while (it.hasNext()) {
                    Task t = it.next();
                    if (t.id == targetId) {
                        it.remove();
                        removed = true;
                        System.out.println(getCurrentTimestamp() + " - [CANCELLED] TaskID:" + targetId);
                        sendResponse("TaskID " + targetId + " cancelled successfully");
                        logTask(targetId, t.scriptName, t.clientName, "CANCELLED");
                        break;
                    }
                }
                if (!removed) {
                    sendRejected("Task " + targetId + " not found or already running.");
                }
            } catch (NumberFormatException e) {
                sendRejected("Invalid TaskID format. TaskID must be an integer.");
            }
        }

        private void sendTaskHistory() {
            StringBuilder sb = new StringBuilder();
            sb.append("Task History:");
            synchronized (taskHistory) {
                if (taskHistory.isEmpty()) {
                    sb.append("\nNo task history available.");
                } else {
                    int idx = 1;
                    for (TaskLog log : taskHistory) {
                        sb.append("\n").append(idx++).append(". TaskID=")
                          .append(log.id).append(", Script=").append(log.scriptName)
                          .append(", Client=").append(log.clientName)
                          .append(", Status=").append(log.status)
                          .append(", Time=").append(log.timestamp);
                    }
                }
            }
            sendResponse(sb.toString());
        }

        private void sendResponse(String message) {
            out.println("STATUS;" + getCurrentTimestamp() + ";" + message);
        }

        private void sendRejected(String reason) {
            System.out.println(getCurrentTimestamp() + " - [REJECTED] " + reason);
            out.println("STATUS;" + getCurrentTimestamp() + ";REJECTED: " + reason);
        }
    }

    private static void processTasks() {
        while (true) {
            try {
                Task task = taskQueue.take();
                String script = task.scriptName;
                if (runningScripts.putIfAbsent(script, true) != null) {
                    System.out.println(getCurrentTimestamp() + " - [DEFERRED] TaskID:" + task.id);
                    taskQueue.put(task);
                    Thread.sleep(100);
                    continue;
                }
                taskExecutor.execute(() -> {
                    try {
                        System.out.println(getCurrentTimestamp() + " - [EXECUTING] TaskID:" + task.id);
                        logTask(task.id, script, task.clientName, "EXECUTING");
                        sendToAllClients("EXECUTING: TaskID " + task.id + " (" + script + ") started execution");

                        Process p = new ProcessBuilder("./" + script).start();
                        int exitCode = p.waitFor();
                        String stat = exitCode == 0 ? "COMPLETED" : "ERROR";
                        System.out.println(getCurrentTimestamp() + " - [" + stat + "] TaskID:" + task.id);
                        sendToAllClients(stat + ": TaskID " + task.id + " (" + script + ") finished with status: " + stat);
                        logTask(task.id, script, task.clientName, stat + (exitCode != 0 ? "_CODE_" + exitCode : ""));
                    } catch (Exception e) {
                        System.err.println(getCurrentTimestamp() + " - [ERROR] TaskID:" + task.id + " | " + e.getMessage());
                        logTask(task.id, script, task.clientName, "ERROR");
                        sendToAllClients("ERROR: TaskID " + task.id + " (" + script + ") encountered an error: " + e.getMessage());
                    } finally {
                        runningScripts.remove(script);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        taskExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(60, TimeUnit.SECONDS)) taskExecutor.shutdownNow();
        } catch (InterruptedException e) {
            taskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void sendToAllClients(String message) {
        String formatted = "STATUS;" + getCurrentTimestamp() + ";" + message;
        synchronized (clientWriters) {
            Iterator<PrintWriter> it = clientWriters.iterator();
            while (it.hasNext()) {
                PrintWriter w = it.next();
                w.println(formatted);
                if (w.checkError()) it.remove();
            }
        }
    }

    private static String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static String getCurrentTimestamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(millis));
    }

    private static synchronized void logTask(int id, String script, String client, String status) {
        taskHistory.add(new TaskLog(id, script, client, status));
    }
}

