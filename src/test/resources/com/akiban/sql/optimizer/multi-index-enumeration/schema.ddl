CREATE TABLE customers(
    id INT NOT NULL,
    PRIMARY KEY(id),
    name VARCHAR(256) NOT NULL,
    vipstatus INT NOT NULL,
    yob INT NOT NULL
);

CREATE INDEX namekey ON customers(name);
CREATE INDEX name_yob ON customers(name, yob);
CREATE INDEX name_vipstatus ON customers(name, vipstatus);

CREATE TABLE orders(
    id INT NOT NULL,
    PRIMARY KEY(id),
    cid INT,
    GROUPING FOREIGN KEY(cid) REFERENCES customers(id),
    odate VARCHAR(256) NOT NULL
);

CREATE INDEX odatekey ON orders(odate);

CREATE TABLE items(
    id INT NOT NULL,
    PRIMARY KEY(id),
    oid INT,
    GROUPING FOREIGN KEY(oid) REFERENCES orders(id),
    name VARCHAR(256) NOT NULL
);

CREATE TABLE addresses(
    id INT NOT NULL,
    PRIMARY KEY(id),
    cid INT,
    GROUPING FOREIGN KEY(cid) REFERENCES customers(id)
);
