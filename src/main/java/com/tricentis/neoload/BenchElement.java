package com.tricentis.neoload;

/**
 * @author lcharlois
 * @since 09/12/2021.
 */
class BenchElement {
    private final String uuid;
    private final Integer objectId;
    private final String name;
    private final Kind kind;
    private final String threadGroupName;


    private BenchElement(final String uuid, final Integer objectId, final String name, final Kind kind, final String threadGroupName) {
        this.uuid = uuid;
        this.objectId = objectId;
        this.name = name;
        this.kind = kind;
        this.threadGroupName = threadGroupName;
    }

    static BenchElement newElement(final String uuid, final Integer objectId, final String name, final Kind kind, final String threadGroupName) {
        return new BenchElement(uuid, objectId, name, kind, threadGroupName);
    }

    String getUuid() {
        return uuid;
    }

    String getName() {
        return name;
    }

    Kind getKind() {
        return kind;
    }

    Integer getObjectId() {
        return objectId;
    }

    public String getThreadGroupName() {
        return threadGroupName;
    }

    enum Kind {
        REQUEST, TRANSACTION;
    }
}
