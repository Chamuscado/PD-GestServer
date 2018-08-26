import DataBaseIO.DataBaseIO;
import DataBaseIO.Exceptions.UserNotFoundException;
import GestServerRmi.GestServerRmi;
import HeartBeats.HeartBeatsGest;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Scanner;

public class GestServer {

    private static int dataBasePorto = 3306;
    private final static String dataBaseUser = "GestServer";
    private final static String dataBasePass = "pd-1718";
    static private DataBaseIO dataBase;
    private static String dataBaseIP = "127.0.0.1";
    private static String dataBaseName = "PD_DataBase";

    static public void main(String[] args) {
        try {
            System.out.println(Inet4Address.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        dataBase = new DataBaseIO(dataBaseName, dataBaseUser, dataBasePass, dataBaseIP);
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

    static public void mainT(String[] args) {//databaseTest
        try {
            System.out.println(Inet4Address.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        dataBase = new DataBaseIO(dataBaseName, dataBaseUser, dataBasePass, dataBaseIP);

        dataBase.connect();
        DataBaseIO.DEBUG = true;
        try {
            dataBase.removePairAtual("Joao");
        } catch (UserNotFoundException e) {
            e.printStackTrace();
        }
        dataBase.refreshAllPlayersWinsAndDefeats();
        dataBase.logoutall();
        dataBase.close();
    }
}
