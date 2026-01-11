package client.net;

import client.Minecraft;
import global.Packets;
import server.Server;

import java.io.*;
import java.net.Socket;

public class SocketClient implements Runnable {
    private final String host;
    private final int port;
    private final String username;
    private Socket socket;
    private static DataOutputStream out;
    private DataInputStream in;

    public SocketClient(String host, int port, String username){
        this.host = host;
        this.port = port;
        this.username = username;
    }

    @Override
    public void run() {
        try {
            socket = new Socket(host, port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            out.writeByte(Packets.AUTH_REQUEST);
            out.writeUTF(username);
            out.flush();

            byte response = in.readByte();
            if (response != Packets.AUTH_SUCCESS) {
                System.err.println("Authentication failed!");
                socket.close();
                return;
            }

            System.out.println("Authenticated successfully as " + username);

            out.writeByte(Packets.REQUEST_LEVEL);
            out.flush();

            while (true) {
                byte packetId = in.readByte();

                switch (packetId) {

                    case Packets.BLOCK_BREAK: {
                        int x = in.readInt();
                        int y = in.readInt();
                        int z = in.readInt();
                        Minecraft.mc.getLevel().setTile(x, y, z, 0);
                        break;
                    }

                    case Packets.BLOCK_PLACE: {
                        int x = in.readInt();
                        int y = in.readInt();
                        int z = in.readInt();
                        Minecraft.mc.getLevel().setTile(x, y, z, 1);
                        break;
                    }

                    case Packets.LEVEL_DATA: {
                        int w = in.readInt();
                        int h = in.readInt();
                        int d = in.readInt();

                        int len = in.readInt();
                        byte[] blocks = new byte[len];
                        in.readFully(blocks);

                        Minecraft.mc.pendingWidth = w;
                        Minecraft.mc.pendingHeight = h;
                        Minecraft.mc.pendingDepth = d;
                        Minecraft.mc.pendingBlocks = blocks;
                        Minecraft.mc.levelUpdatePending = true;
                        break;
                    }

                    case Packets.CHAT: {
                        String author = in.readUTF();
                        String message = in.readUTF();
                        Minecraft.mc.chat.addMessage(author, message);
                        break;
                    }

                    default:
                        System.err.println("Unknown packet: " + packetId);
                        break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendBlock(int packet, int x, int y, int z) throws IOException {
        out.writeByte(packet);
        out.writeInt(x);
        out.writeInt(y);
        out.writeInt(z);
        out.flush();
    }

    public static void sendChat(String author, String message) throws IOException {
        out.writeByte(Packets.CHAT);
        out.writeUTF(author);
        out.writeUTF(message);
        out.flush();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}
