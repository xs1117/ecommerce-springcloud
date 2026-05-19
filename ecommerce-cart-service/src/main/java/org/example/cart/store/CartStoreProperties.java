package org.example.cart.store;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cart.store")
public class CartStoreProperties {

    private ReadStore read = ReadStore.REDIS;
    private WriteStore write = WriteStore.REDIS;

    public ReadStore getRead() {
        return read;
    }

    public void setRead(ReadStore read) {
        this.read = read;
    }

    public WriteStore getWrite() {
        return write;
    }

    public void setWrite(WriteStore write) {
        this.write = write;
    }

    public enum ReadStore {
        REDIS,
        DB
    }

    public enum WriteStore {
        REDIS,
        DB,
        BOTH
    }
}

