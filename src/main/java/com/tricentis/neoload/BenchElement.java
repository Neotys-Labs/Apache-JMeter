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


	private BenchElement(final String uuid, final Integer objectId, final String name, final Kind kind) {
		this.uuid = uuid;
		this.objectId = objectId;
		this.name = name;
		this.kind = kind;
	}

	static BenchElement newElement(final String uuid, final Integer objectId, final String name, final Kind kind) {
		return new BenchElement(uuid, objectId, name, kind);
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

	enum Kind {
		REQUEST, TRANSACTION;
	}
}
