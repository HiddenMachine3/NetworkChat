package com.hidden_machine.network_chat;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Scanner;

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class Client {

    String serverAddress;
    Scanner in;
    PrintWriter out;
    JFrame frame = new JFrame("Chatter");
    JTextField textField = new JTextField(50);
    JTextArea messageArea = new JTextArea(16, 50);

    String letter_case = "BOTH";
    int port_number = 59001;

    String current_client_name = "";

    boolean private_mode = false;
    HashSet<String> private_mode_clients = new HashSet<>();

    /**
     * Constructs the client by laying out the GUI and registering a listener with
     * the textfield so that pressing Return in the listener sends the textfield
     * contents to the server. Note however that the textfield is initially NOT
     * editable, and only becomes editable AFTER the client receives the
     * NAMEACCEPTED message from the server.
     */
    public Client(String serverAddress, int port_number) {
        this.port_number = port_number;
        this.serverAddress = serverAddress;
        textField.setEditable(false);
        messageArea.setEditable(false);
        frame.getContentPane().add(textField, BorderLayout.SOUTH);
        frame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.pack();

        // Send on enter then clear to prepare for next message
        textField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String line = textField.getText();
                if (line.startsWith("CMD:")) {
                    String[] arguments = null;

                    int cmd_command_end_index = line.indexOf(" ");
                    if (cmd_command_end_index == -1)
                        cmd_command_end_index = line.length();

                    String cmd_command = line.substring(line.indexOf(":") + 1, cmd_command_end_index);
                    switch (cmd_command) {
                        case "CLR":
                            messageArea.setText("");
                            break;
                        case "CAPITAL":
                        case "SMALL":
                        case "BOTH":
                            System.out.println("CASE CHANGE : " + line);
                            letter_case = cmd_command;

                            out.println(";" + private_mode + ";" + line);

                            break;

                        case "ID":
                            messageArea.append("Server Port ID: " + port_number + "\n");
                            break;
                        case "BLOCK":
                            arguments = line.substring(4).split(" ");
                            HashSet<String> blocked_clients = new HashSet<>();

                            for (int i = 1; i < arguments.length; i++) {
                                if (arguments[i].equals(current_client_name)) {
                                    messageArea.append("ERROR: You cannot block yourself!!!\n");
                                    continue;
                                }
                                blocked_clients.add(arguments[i]);
                            }
                            if (!blocked_clients.isEmpty())
                            out.println("BLOCK " + String.join(" ", blocked_clients));
                            break;
                        case "UNBLOCK":
                            HashSet<String> unblocked_clients = new HashSet<>();
                            arguments = line.substring(4).split(" ");
                            for (int i = 1; i < arguments.length; i++) {
                                if (arguments[i].equals(current_client_name)) {
                                    messageArea.append("ERROR: You cannot unblock yourself!!!\n");
                                    continue;
                                }
                                unblocked_clients.add(arguments[i]);
                            }

                            out.println("UNBLOCK " + String.join(" ", unblocked_clients));
                            break;
                        case "PM":
                            arguments = line.substring(4).split(" ");
                            String pm_command = arguments[1];
                            switch (pm_command) {
                                case "ENTER":// to switch on private mode
                                    private_mode = true;
                                    break;
                                case "LEAVE":// to swtich off private mode
                                    private_mode = false;
                                    break;
                                case "PRINT":
                                    messageArea.append("Private mode is currently : " + (private_mode ? "ON" : "OFF") + "\n");
                                    if (!private_mode_clients.isEmpty()) {
                                        messageArea.append("The clients in your private group are : " + String.join(", ", private_mode_clients) + "\n");
                                    }
                                    break;
                                case "ADD":
                                    LinkedList<String> new_clients = new LinkedList<>();
                                    for (int i = 2; i < arguments.length; i++) {
                                        String client_name = arguments[i];
                                        private_mode_clients.add(client_name);
                                        new_clients.add(client_name);
                                    }
                                    if (!new_clients.isEmpty())
                                        out.println("PM ADD " + String.join(" ", new_clients));

                                    break;
                                case "REMOVE":
                                    LinkedList<String> new_clients_to_remove = new LinkedList<>();
                                    for (int i = 2; i < arguments.length; i++) {
                                        String client_name = arguments[i];
                                        private_mode_clients.remove(client_name);
                                        new_clients_to_remove.add(client_name);
                                    }
                                    if (!new_clients_to_remove.isEmpty())
                                        out.println("PM REMOVE " + String.join(" ", new_clients_to_remove));
                                    break;
                            }

                    }

                } else {
                    out.println(";" + private_mode + ";" + line);
                }
                textField.setText("");

            }
        });
    }

    private String getName() {
        return JOptionPane.showInputDialog(frame, "Choose a screen name:", "Screen name selection",
                JOptionPane.PLAIN_MESSAGE);
    }

    private void run() throws IOException {
        try {
            var socket = new Socket(serverAddress, port_number);
            in = new Scanner(socket.getInputStream());
            out = new PrintWriter(socket.getOutputStream(), true);

            while (in.hasNextLine()) {
                var line = in.nextLine();
                if (line.startsWith("SUBMITNAME")) {
                    String name = getName();
                    current_client_name = name;
                    out.println(name);
                } else if (line.startsWith("NAMEACCEPTED")) {
                    this.frame.setTitle("Chatter - " + line.substring(13));
                    textField.setEditable(true);
                } else if (line.startsWith("MESSAGE")) {
                    System.out.println(line.substring(8));
                    String modified_line = line.substring(8);
                    if (letter_case.equals("CAPITAL")) {
                        modified_line = modified_line.toUpperCase();
                    } else if (letter_case.equals("SMALL")) {
                        modified_line = modified_line.toLowerCase();
                    }
                    System.out.println(modified_line);
                    messageArea.append(modified_line + "\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            frame.setVisible(false);
            frame.dispose();
        }
    }

    public static void main(String[] args) throws Exception {
        String server_ip = "localhost"; // default server ip address
        int port_id = 59001; //default port number
        if (args.length > 0) {
            server_ip = args[0];
            if (args.length >1) {
                port_id = Integer.parseInt(args[1]);
            }
        }
        Client client = new Client(server_ip, port_id);
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.setVisible(true);
        client.run();
    }
}