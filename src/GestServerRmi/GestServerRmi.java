package GestServerRmi;

import DataBaseIO.DataBaseIO;
import DataBaseIO.Elements.PairDatabase;
import DataBaseIO.Elements.PlayerDatabase;
import DataBaseIO.Exceptions.*;
import Elements.*;
import Exceptions.AccessDeniedException;
import Exceptions.UserAlreadyLoggedException;
import HeartBeats.HeartBeatsGest;
import HeartBeats.IHeartBeatsGestParent;
import Interfaces.IClientRmi;
import Interfaces.IGestServerRmi;

import java.io.Serializable;
import java.rmi.AccessException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class GestServerRmi extends UnicastRemoteObject implements IGestServerRmi, Serializable, IHeartBeatsGestParent {

    private static final int MaxMsg = 20;
    private static Registry registry = null;
    private static GestServerRmi server = null;
    private static DataBaseIO dataBase = null;
    private final HashMap<String, IClientRmi> userList = new HashMap<>();
    private final HashMap<MessagePair, LinkedList<Message>> messages = new HashMap<MessagePair, LinkedList<Message>>();
    private HeartBeatsGest heartBeatsGest;
    private String gameServerIP;

    static public void startGestServerRmi(HeartBeatsGest heartBeatsGest, String regist, DataBaseIO database) {
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
            server.heartBeatsGest = heartBeatsGest;
            heartBeatsGest.setParent(server);
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
            return dataBase.addPlayer(new PlayerDatabase(name, username, password));
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

            if (dataBase.login(new PlayerDatabase(username, password, getClientHost(), 0))) {
                synchronized (userList) {
                    userList.put(username, rmiInterface);
                }
            }
            refreshClientLoginList();
            new Thread(() -> {
                try {
                    rmiInterface.refreshStatus();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }).start();
            return true;
        } catch (UserNotFoundException | WrongPassWordException | ServerNotActiveException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }


    @Override
    public void logOut(String username, IClientRmi rmiInterface, ValidationUser validation) throws RemoteException {
        System.out.println("TryLogout: " + username);
        if (rmiInterface == null || username == null || username.isEmpty())
            return;
        IClientRmi clientRmi = null;
        synchronized (userList) {
            clientRmi = userList.get(username);
        }
        if (clientRmi == null)
            return;
        if (isValidUser(validation)) {
            logoutUser(username);
        }
    }

    @Override
    public List<User> getLoginUsers(ValidationUser validation) throws RemoteException, AccessDeniedException {
        System.out.println("<getLoginUsers> " + validation.getUsername());
        if (!isValidUser(validation))
            throw new AccessDeniedException();
        List<PlayerDatabase> list = dataBase.getPlayersLogados();
        List<User> userList = new ArrayList<>(list.size());
        for (PlayerDatabase player : list) {
            System.out.print(player);
            if (validation.getUsername().compareTo(player.getUser()) != 0) {
                // System.out.println(" -> adicionado");
                userList.add(new User(player.getName(), player.getUser(), "", player.getIdPar() != PairDatabase.INVALIDID));
            } //else System.out.println(" -> não adicionado");
        }
        return userList;
    }

    @Override
    public boolean sendMensage(Message message, ValidationUser validation) throws RemoteException, AccessDeniedException {
        if (!isValidUser(validation))
            throw new AccessDeniedException();

        System.out.println("sendMensage: " + message);

        MessagePair pair;
        if (message.getDest().equals(IClientRmi.ForAll) || message.getSource().equals(IClientRmi.ForAll))
            pair = new MessagePair(IClientRmi.ForAll, IClientRmi.ForAll);
        else
            pair = new MessagePair(message.getDest(), message.getSource());
        LinkedList<Message> messages = this.messages.get(pair);
        if (messages != null) {
            messages.add(message);
            while (messages.size() > MaxMsg)
                messages.removeFirst();
        } else {
            messages = new LinkedList<>();
            messages.add(message);
            this.messages.put(pair, messages);
        }

        if (pair.contains(IClientRmi.ForAll)) {
            userList.forEach((s, client) -> {
                try {
                    client.refreshMessagesFor(IClientRmi.ForAll);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
            return true;
        }
        IClientRmi destIRmi = userList.get(message.getDest());
        destIRmi.refreshMessagesFor(message.getSource());
        destIRmi = userList.get(message.getSource());
        destIRmi.refreshMessagesFor(message.getDest());
        return true;
    }

    @Override
    public void sendPairInvite(String dest, ValidationUser validationUser) throws RemoteException, AccessDeniedException {
        IClientRmi destIRmi = userList.get(dest);
        if (!isValidUser(validationUser) || destIRmi == null)
            throw new AccessDeniedException();
        try {
            PlayerDatabase player = dataBase.getPlayer(validationUser.getUsername());
            destIRmi.recivePairInvite(new User(player.getName(), player.getUser()));
        } catch (UserNotFoundException e) {
            System.out.println("<SendPairInvite> " + e.getMessage());
        }
    }

    @Override
    public void answerPairInvite(User inviter, ValidationUser validationUser, boolean answer) throws RemoteException, AccessDeniedException {
        IClientRmi destIRmi = userList.get(inviter.username);
        if (!isValidUser(validationUser) || destIRmi == null)
            throw new AccessDeniedException();
        IClientRmi sourceIRmi = userList.get(validationUser.getUsername());
        if (sourceIRmi == null || inviter.username.compareTo(validationUser.getUsername()) == 0)
            return;
        destIRmi.answerPairInvite(inviter, answer);
        if (answer) {
            try {
                List<IClientRmi> clients = new ArrayList<>();
                try {
                    PairDatabase pair = dataBase.getPairAtual(validationUser.getUsername());
                    IClientRmi cli = userList.get(pair.getPlayerDatabases(0).getUser());
                    if (cli != null) clients.add(cli);
                    cli = userList.get(pair.getPlayerDatabases(1).getUser());
                    if (cli != null) clients.add(cli);
                } catch (PairAtualNotFoundException ignore) {
                    clients.add(sourceIRmi);
                }

                try {
                    PairDatabase pair = dataBase.getPairAtual(inviter.username);
                    IClientRmi cli = userList.get(pair.getPlayerDatabases(0).getUser());
                    if (cli != null) clients.add(cli);
                    cli = userList.get(pair.getPlayerDatabases(1).getUser());
                    if (cli != null) clients.add(cli);
                } catch (PairAtualNotFoundException ignore) {
                    clients.add(destIRmi);
                }

                dataBase.createpair(validationUser.getUsername(), inviter.username);
                dataBase.setPairAtual(validationUser.getUsername(), inviter.username);

                for (IClientRmi client : clients) {
                    client.refreshStatus();
                }
            } catch (UserNotFoundException | InvalidPairException | PairNotFoundException e) {
                System.out.println("<AnswerPairInvite> " + e.getMessage());
            }
        }

    }

    @Override
    public void endPair(ValidationUser validationUser) throws RemoteException, AccessDeniedException {
        if (!isValidUser(validationUser))
            throw new AccessDeniedException();

        try {
            PairDatabase pair = dataBase.getPairAtual(validationUser.getUsername());
            dataBase.removePairAtual(validationUser.getUsername());
            refreshClientPairs(pair);
        } catch (UserNotFoundException | PairAtualNotFoundException ignore) {
        }
    }

    @Override
    public Pair getMyPair(ValidationUser validationUser) throws RemoteException, AccessDeniedException {
        if (!isValidUser(validationUser))
            throw new AccessDeniedException();

        try {
            PairDatabase pair = dataBase.getPairAtual(validationUser.getUsername());

            PlayerDatabase player0 = pair.getPlayerDatabases(0);
            PlayerDatabase player1 = pair.getPlayerDatabases(1);
            return new Pair(new User(player0.getName(), player0.getUser()), new User(player1.getName(), player1.getUser()));
        } catch (UserNotFoundException | PairAtualNotFoundException ignore) {
        }
        return null;
    }

    @Override
    public Status getStatus(ValidationUser validationUser) throws RemoteException, AccessDeniedException {
        if (!isValidUser(validationUser))
            throw new AccessDeniedException();
        return getStatus(validationUser.getUsername());
    }

    @Override
    public Status getStatus(String username, ValidationUser validationUser) throws RemoteException, AccessDeniedException {
        if (!isValidUser(validationUser))
            throw new AccessDeniedException();
        return getStatus(username);
    }

    @Override
    public String getGameServerIp(ValidationUser validationUser) throws RemoteException, AccessDeniedException {
        if (isValidUser(validationUser))
            return gameServerIP;
        else
            throw new AccessDeniedException();
    }

    @Override
    public List<Message> getMessages(MessagePair messagePair, ValidationUser validationUser) throws RemoteException, AccessDeniedException {
        if (!isValidUser(validationUser))
            throw new AccessDeniedException();
        System.out.println("Pedido de mensagens para: " + messagePair);
        List<Message> list = this.messages.get(messagePair);
        if (list != null)
            for (Message msg : list) {
                System.out.println(msg);
            }
        return list;
    }


    private Status getStatus(String username) throws RemoteException, AccessDeniedException {
        try {
            PlayerDatabase player = dataBase.getPlayer(username);

            IClientRmi client = userList.get(username);
            if (client != null)
                client.setReadyToPlay(player.getIdPar() != PairDatabase.INVALIDID);

            Status status = new Status(new User(player.getName(), player.getUser()), player.getWins(), player.getDefeats());
            return status;
        } catch (UserNotFoundException ignore) { //Para chegar aqui tem de passar pela validaçao do user
        }
        return null;
    }

    private boolean isValidUser(ValidationUser validation) throws RemoteException {
        IClientRmi client = userList.get(validation.getUsername());
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
                    usersOffline.forEach(s -> logoutUser(s));
                }
            }
        }).start();
    }

    private void logoutUser(String username) {
        userList.remove(username);
        IClientRmi cli = null;
        String otherUsername = "";
        try {
            PairDatabase pair = dataBase.getPairAtual(username);
            if (username.compareTo(pair.getPlayerDatabases(0).getUser()) == 0) {
                cli = userList.get(otherUsername = pair.getPlayerDatabases(1).getUser());
            } else {
                cli = userList.get(otherUsername = pair.getPlayerDatabases(0).getUser());
            }
            dataBase.removePairAtual(username);
        } catch (UserNotFoundException | PairAtualNotFoundException ignored) {
        } finally {

            dataBase.logout(username);
            if (cli != null) {
                try {
                    cli.refreshStatus();
                } catch (RemoteException e) {
                    userList.remove(otherUsername);
                }
            }
        }

        System.out.println("A fazer logout do user " + username);
        refreshClientLoginList();
    }

    private void refreshClientPairs(PairDatabase pair) {
        try {
            refreshClientStatus(pair.getPlayerDatabases(0).getUser(), pair.getPlayerDatabases(1).getUser());
        } catch (UserNotFoundException ignore) {
        }
    }

    private void refreshClientStatus(String user0, String user1) {
        refreshClientStatus(user0);
        refreshClientStatus(user1);

    }

    private void refreshClientStatus(String user) {
        IClientRmi client = userList.get(user);
        if (client != null) {
            try {
                client.refreshStatus();
            } catch (RemoteException e) {
                userList.remove(user);
            }
        }
    }

    @Override
    public void GameServerDisconect() {
        gameServerIP = null;
        System.out.println("Servidor de Jogo Desconectado");
    }

    @Override
    public void setGameServerIp(String gameServerIP) {
        this.gameServerIP = gameServerIP;
        System.out.println("Servidor de Jogo Conectado -> " + gameServerIP);
    }
}
