package HeartBeats;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IHeartBeats extends Remote {
    String heartBeat() throws RemoteException;
}
