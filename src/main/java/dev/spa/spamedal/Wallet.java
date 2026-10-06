package dev.spa.spamedal;

/** 両替で動かすスパコインの口座。本番は Vault（EssentialsX）、検証では差し替える。 */
interface Wallet {

    double balance();

    boolean withdraw(double amount);

    boolean deposit(double amount);
}
