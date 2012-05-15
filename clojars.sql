create table users
       (id SERIAL PRIMARY KEY,
        username TEXT UNIQUE NOT NULL,
        password TEXT NOT NULL,
        salt TEXT NOT NULL,
        email TEXT NOT NULL,
        ssh_key TEXT NOT NULL,
        created DATE NOT NULL);

create table jars
       (id SERIAL PRIMARY KEY,
        group_name TEXT NOT NULL,
        jar_name TEXT NOT NULL,
        version TEXT NOT NULL,
        username TEXT NOT NULL,
        created DATE NOT NULL,
        description TEXT,
        homepage TEXT,
        scm TEXT,
        authors TEXT);
        
create table deps
       (id SERIAL PRIMARY KEY,
        group_name TEXT NOT NULL,
        jar_name TEXT NOT NULL,
        version TEXT NOT NULL,
        dep_group_name TEXT NOT NULL,
        dep_jar_name TEXT NOT NULL,
        dep_version TEXT NOT NULL);

create table groups
       (id SERIAL PRIMARY KEY,
        name TEXT NOT NULL,
        username TEXT NOT NULL);
