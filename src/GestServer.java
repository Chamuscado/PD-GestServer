import DataBaseIO.DataBaseIO;
import DataBaseIO.Exceptions.UserNotFoundException;
import GestServerRmi.GestServerRmi;
import HeartBeats.HeartBeatsGest;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Scanner;

public class GestServer {

    private static int dataBasePorto = 3306;
    private static String dataBaseUser = "GestServer";
    private static String dataBasePass = "pd-1718";
    private static String dataBaseName = "PD_DataBase";

    static public void main(String[] args) {

        String dataBaseIP;

        if (args.length != 1 && args.length != 4 && args.length != 5) {
            System.out.println("Sintaxe: java GestServer <dataBaseIP>");
            System.out.println("Sintaxe: java GestServer <dataBaseIP> <dataBaseName> <dataBaseUser> <dataBasePass>");
            System.out.println("Sintaxe: java GestServer <dataBaseIP> <dataBaseName> <dataBaseUser> <dataBasePass> <dataBasePorto>");
            return;
        }
        dataBaseIP = args[0];
        if (!validIP(dataBaseIP)) {
            System.out.println("Endereço de IP inválido");
            return;
        }
        if (args.length >= 4) {
            dataBaseName = args[1];
            dataBaseUser = args[2];
            dataBasePass = args[3];
        }
        if (args.length == 5) {
            try {
                dataBasePorto = Integer.parseInt(args[4]);
            } catch (NumberFormatException ignore) {
                System.out.println("Porto da Base de Dados inválido");
                return;
            }
        }
        try {
            System.out.println(Inet4Address.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        DataBaseIO dataBase = new DataBaseIO(dataBaseName, dataBaseUser, dataBasePass, dataBaseIP);
        //  DataBaseIO.DEBUG = true;
        dataBase.connect();
        dataBase.logoutall();
        HeartBeatsGest heartBeatsGest = HeartBeatsGest.startHeartBeatsGest("localhost", dataBaseIP + ":" + dataBasePorto);
        GestServerRmi.startGestServerRmi(heartBeatsGest, "localhost", dataBase);

        //  heartBeatsGest.DEBUG = true;

        Scanner in = new Scanner(System.in);
        String cmd;
        do {
            cmd = in.nextLine();
        } while (cmd.compareTo("stop") != 0);

        GestServerRmi.stop();
        HeartBeatsGest.stop();

        dataBase.logoutall();
        dataBase.close();
    }

    private static boolean validIP(String ip) {
        try {
            if (ip == null || ip.isEmpty()) {
                return false;
            }
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return false;
            }
            for (String s : parts) {
                int i = Integer.parseInt(s);
                if (i < 0 || i > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException ignore) {
            return false;
        }
    }

}
