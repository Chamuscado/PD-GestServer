package HeartBeats;

import java.io.Serializable;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;

public class HeartBeatsGest extends UnicastRemoteObject implements IHeartBeats, Serializable {
    private String dataBaseIp;
    private static Registry registry = null;
    private static HeartBeatsGest server = null;
    private static String registration = null;
    private static String serviceName = null;

    static public void startHeartBeatsGest(String regist, String serviceStr, String dataBaseIp) {

        try {
            try {
                registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);

            } catch (ConnectException ignore) {
                try {
                    registry = LocateRegistry.getRegistry(Registry.REGISTRY_PORT);
                } catch (RemoteException e) {
                    System.err.println("Remote Error (" + serviceStr + ") - " + e);
                }
            }


            server = new HeartBeatsGest(dataBaseIp);
            registration = "rmi://" + regist + "/" + serviceStr;
            serviceName = serviceStr;
            Naming.rebind(registration, server);

        } catch (RemoteException e) {
            System.err.println("Remote Error (" + serviceStr + ") - " + e);
            registry = null;
            server = null;
            registration = null;
        } catch (Exception e) {
            System.err.println("Error (" + serviceStr + ") - " + e);
            registry = null;
            server = null;
            registration = null;
        }
    }


    protected HeartBeatsGest(String dataBaseIp) throws RemoteException {
        this.dataBaseIp = dataBaseIp;
    }

    protected HeartBeatsGest(int port, String dataBaseIp) throws RemoteException {
        super(port);
        this.dataBaseIp = dataBaseIp;
    }

    protected HeartBeatsGest(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf, String dataBaseIp) throws RemoteException {
        super(port, csf, ssf);
        this.dataBaseIp = dataBaseIp;
    }

    public static void stop() {
        if (registry == null) {
            System.out.println("Nenhum serviço iniciado");
            return;
        }
        try {
            registry.unbind(serviceName);
            UnicastRemoteObject.unexportObject(server, true);

        } catch (RemoteException | NotBoundException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    public String heartBeat() throws RemoteException {
        try {
            System.out.println(getClientHost());
        } catch (ServerNotActiveException e) {
            e.printStackTrace();
        }
        return dataBaseIp;
    }
}

