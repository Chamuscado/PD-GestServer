package GestServerRmi;

import DataBaseIO.DataBaseIO;
import DataBaseIO.Elements.Player;
import DataBaseIO.Exceptions.*;
import Elements.Message;
import Elements.User;
import Elements.ValidationUser;
import Exceptions.AccessDeniedException;
import Exceptions.UserAlreadyLoggedException;
import Interfaces.IClientRmi;
import Interfaces.IGestServerRmi;

import java.io.Serializable;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class GestServerRmi extends UnicastRemoteObject implements IGestServerRmi, Serializable {

    private static Registry registry = null;
    private static GestServerRmi server = null;
    private static DataBaseIO dataBase = null;
    private final HashMap<String, IClientRmi> userList = new HashMap<>();


    static public void startGestServerRmi(String regist, DataBaseIO database) {
        dataBase = database;
        String registration = null;

        try {
            registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
        } catch (RemoteException ignore) {
            try {
                registry = LocateRegistry.getRegistry(Registry.REGISTRY_PORT);

            } catch (RemoteException e) {
                System.err.println("Remote Error (" + IGestServerRmi.ServiceName + ") - " + e);
            }
        }

        try {
            server = new GestServerRmi();
            registration = "rmi://" + regist + "/" + IGestServerRmi.ServiceName;
            Naming.rebind(registration, server);

        } catch (RemoteException e) {
            System.err.println("Remote Error (" + IGestServerRmi.ServiceName + ")- " + e);
            registry = null;
            server = null;
        } catch (Exception e) {
            System.err.println("Error (" + IGestServerRmi.ServiceName + ") - " + e);
            registry = null;
            server = null;
        }
    }


    private GestServerRmi() throws RemoteException {
    }

    protected GestServerRmi(int port) throws RemoteException {
        super(port);
    }

    protected GestServerRmi(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
    }


    public static void stop() {
        if (registry == null) {
            System.out.println("Nenhum serviço iniciado");
            return;
        }
        try {
            registry.unbind(IGestServerRmi.ServiceName);
            UnicastRemoteObject.unexportObject(server, true);

        } catch (RemoteException | NotBoundException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean registUser(String name, String username, String password) throws RemoteException {
        System.out.println("A registar o user " + name + "registUser ");
        try {
            return dataBase.addPlayer(new Player(name, username, password));
        } catch (InvalidUserException | UseAlreadExitsException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    @Override
    public boolean login(String username, String password, IClientRmi rmiInterface) throws RemoteException, UserAlreadyLoggedException {
        System.out.println("A fazer login do user " + username);
        try {
            if (dataBase.getPlayer(username).isLogado())
                throw new UserAlreadyLoggedException();

            if (dataBase.login(new Player(username, password, getClientHost(), 0))) {
                synchronized (userList) {
                    userList.put(username, rmiInterface);
                }
            }
            refreshClientLoginList();
            return true;
        } catch (UserNotFoundException | WrongPassWordException | ServerNotActiveException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }


    @Override
    public void logOut(String username, IClientRmi rmiInterface) throws RemoteException {
        System.out.println("TryLogout: " + username);
        if (rmiInterface == null || username == null || username.isEmpty())
            return;
        IClientRmi clientRmi = null;
        synchronized (userList) {
            clientRmi = userList.get(username);
        }
        if (clientRmi == null)
            return;
        if (rmiInterface.getCode() == clientRmi.getCode()) {
            dataBase.logout(username);
            refreshClientLoginList();
        }
    }

    @Override
    public List<User> getLoginUsers(ValidationUser validation) throws RemoteException, AccessDeniedException {
        if (!isValidUser(validation))
            throw new AccessDeniedException();
        List<Player> list = dataBase.getPlayersLogados();
        List<User> userList = new ArrayList<>(list.size());
        for (Player player : list) {
            if (validation.getUser().compareTo(player.getName()) != 0)
                userList.add(new User(player.getName(), player.getUser(), ""));
        }

        return userList;
    }

    @Override
    public boolean createPair(String user0, String user1, ValidationUser validation) throws RemoteException, AccessDeniedException {       // TODO -> têm de pedir a outro par
        if (true)//TODO -> CheckLogin
            throw new AccessDeniedException();
        try {
            boolean resp = dataBase.createpair(user0, user1);
            dataBase.setPairAtual(user0, user1);
            return resp;
        } catch (UserNotFoundException | InvalidPairException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendMensage(Message message, ValidationUser validation) throws RemoteException {
        IClientRmi destIRmi = userList.get(message.getDest());
        if (!isValidUser(validation) || destIRmi == null)
            return false;
        destIRmi.sendMensage(message);
        return true;
    }

    @Override
    public void SendPairInvite(String s, ValidationUser validationUser) throws RemoteException {

    }

    private boolean isValidUser(ValidationUser validation) throws RemoteException {
        IClientRmi client = userList.get(validation.getUser());
        if (client == null || client.getCode() != validation.getCode())
            return false;
        else return true;
    }

    private void refreshClientLoginList() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> usersOffline = new ArrayList<>();
                synchronized (userList) {
                    userList.forEach((key, value) -> {
                        try {
                            value.refreshLoginUsers();
                        } catch (RemoteException e) {
                            usersOffline.add(key);
                        }
                    });
                    usersOffline.forEach(s -> {
                        userList.remove(s);
                        dataBase.logout(s);
                    });
                }
            }
        }).start();
    }
}
