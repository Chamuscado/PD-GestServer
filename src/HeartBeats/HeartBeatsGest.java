package HeartBeats;

import java.io.Serializable;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;

public class HeartBeatsGest extends UnicastRemoteObject implements IHeartBeats, Serializable {


    static public void startHeartBeatsGest(String registry, String serviceStr) {
        try {
            LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
            HeartBeatsGest server = new HeartBeatsGest();
            String registration = "rmi://" + registry + "/" + serviceStr;
            Naming.rebind(registration, server);
        } catch (RemoteException e) {
            System.err.println("Remote Error - " + e);
        } catch (Exception e) {
            System.err.println("Error - " + e);
        }
    }


    protected HeartBeatsGest() throws RemoteException {
    }

    protected HeartBeatsGest(int port) throws RemoteException {
        super(port);
    }

    protected HeartBeatsGest(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
    }
    @Override
    public String heartBeat() throws RemoteException {
        try {
            System.out.println(getClientHost());
        } catch (ServerNotActiveException e) {
            e.printStackTrace();
        }
        return "123.456.789.012";
    }
}

