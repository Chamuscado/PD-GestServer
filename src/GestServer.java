import DataBaseIO.DataBaseIO;
import GestServerRmi.GestServerRmi;
import HeartBeats.HeartBeatsGest;

import java.io.IOException;
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

        dataBase.connect();

        HeartBeatsGest.startHeartBeatsGest("localhost", "RemoteT", dataBaseIP + ":" + dataBasePorto);
        GestServerRmi.startGestServerRmi("localhost", dataBase);

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
}
