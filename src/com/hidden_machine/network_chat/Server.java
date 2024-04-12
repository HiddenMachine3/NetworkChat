package com.hidden_machine.network_chat;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Executors;

public class Server {

    // All client names, so we can check for duplicates upon registration.
    private static Set<String> names = new HashSet<>();

    // The set of all the print writers for all the clients, used for broadcast.
    private static Set<PrintWriter> writers = new HashSet<>();

    private static HashMap<String, PrintWriter> name_writer_map = new HashMap<>();
    private static HashMap<PrintWriter, String> writer_name_map = new HashMap<>();

    private static HashMap<String, HashSet<String>> block_list = new HashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running...");
        var pool = Executors.newFixedThreadPool(500);
        try (var listener = new ServerSocket(59001)) {
            while (true) {
                pool.execute(new Handler(listener.accept()));
            }
        }
    }

    /**
     * The client handler task.
     */
    private static class Handler implements Runnable {
        private String name;
        private Socket socket;
        private Scanner in;
        private PrintWriter out;

        /**
         * Constructs a handler thread, squirreling away the socket. All the interesting
         * work is done in the run method. Remember the constructor is called from the
         * server's main method, so this has to be as short as possible.
         */
        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Services this thread's client by repeatedly requesting a screen name until a
         * unique one has been submitted, then acknowledges the name and registers the
         * output stream for the client in a global set, then repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {
                in = new Scanner(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);

                // Keep requesting a name until we get a unique one.
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.nextLine();
                    if (name == null) {
                        return;
                    }
                    synchronized (names) {
                        if (!name.isBlank() && !names.contains(name)) {
                            names.add(name);
                            break;
                        }
                    }
                }

                // Now that a successful name has been chosen, add the socket's print writer
                // to the set of all writers so this client can receive broadcast messages.
                // But BEFORE THAT, let everyone else know that the new person has joined!
                out.println("NAMEACCEPTED " + name);
                for (PrintWriter writer : writers) {
                    writer.println("MESSAGE " + name + " has joined");
                }
                writers.add(out);
                name_writer_map.put(name, out); // we want to associate this name with this printWriter object
                writer_name_map.put(out, name);

                // Accept messages from this client and broadcast them.
                while (true) {
                    String input = in.nextLine();
                    if (input.toLowerCase().startsWith("/quit")) {
                        return;
                    }
                    if (input.startsWith("BLOCK")) {

                        String words[] = input.split(" ");

                        for (int i = 1; i < words.length; i++) {
                            String client_name = words[i];
                            if (!block_list.containsKey(name)) {
                                block_list.put(name, new HashSet<>());
                            }
                            block_list.get(name).add(client_name);
                            System.out.println(name + " has blocked " + client_name);
                        }
                        System.out.println(block_list.toString());
                    } else if (input.startsWith("UNBLOCK")) {
                        String words[] = input.split(" ");

                        for (int i = 1; i < words.length; i++) {
                            String client_name = words[i];
                            if (!block_list.containsKey(name)) {
                                break;
                            }
                            block_list.get(name).remove(client_name);
                            System.out.println(name + " has unblocked " + client_name);

                        }
                        System.out.println(block_list.toString());
                    } else {
                        for (PrintWriter writer : writers) {

                            // if <name> had blocked someone, they are not allowing the PrinterWriter
                            // associated with that person to send messages to them, and vice versa
                            String name_of_writer = writer_name_map.get(writer);

                            if (block_list.getOrDefault(name, new HashSet<>()).contains(name_of_writer))
                                continue;

                            if (block_list.getOrDefault(name_of_writer, new HashSet<>()).contains(name))
                                continue;

                            writer.println("MESSAGE " + name + ": " + input);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println(e);
            } finally {
                if (out != null) {
                    writers.remove(out);
                }
                if (name != null) {
                    System.out.println(name + " is leaving");
                    names.remove(name);
                    for (PrintWriter writer : writers) {
                        writer.println("MESSAGE " + name + " has left");
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
