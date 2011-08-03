Clojars web interface
=====================

This is the source code for the [Clojars](http://clojars.org/) jar
repository.  If you're looking for user documentation, try the
[wiki](http://github.com/ato/clojars-web/wiki/_pages).

This is only of interest to you if you want to hack on the Clojars
source code (send me a pull request with your patches) or if you want
to try to run your own copy (for example inside a company).

Running the webapp
------------------

1. Install [Leiningen](http://github.com/technomancy/leiningen) and
[SQLite3](http://www.sqlite.org/)
(Debian: `apt-get install sqlite3`). 

2. Create an initial sqlite database: `mkdir data; sqlite3 data/db < clojars.sql`

3. Run the webapp: `lein run 8080 8701`

4. Now try hitting `http://localhost:8080/` in your web browser.

If you'd like to run it out of an editor/IDE environment you can
probably eval a call to the `-main` function in
`src/clojars/core.clj`.

Running in a production-like environment (Linux VM)
---------------------------------------------------

1. Install [VirtualBox 4](http://www.virtualbox.org/wiki/Downloads).
2. Install [Vagrant](http://vagrantup.com) by running: <tt>gem install vagrant</tt>. You may need to install [rubygems](http://rubygems.org) first.
3. Run <tt>vagrant up</tt> and wait, it'll take a while.

Test data
---------

If you'd like to hack on the UI or search it might be useful to have
production-like metadata.  I've put up a production database dump
(with password hashes and email addresses stripped of course) which
you can use like this:
    
    wget http://meshy.org/~ato/clojars-test-data.sql.gz
    rm data/db
    zcat clojars-test-data.sql.gz | sqlite3 data/db

If you want all the actual jar files as well you can grab them via
[rsync](http://github.com/ato/clojars-web/wiki/Data).

SSH integration
---------------

The SSH integration is kind of a hack and needs improvement.
Currently it uses [Nailgun](http://martiansoftware.com/nailgun/) but
the current setup has threading problems due to the way it does IO.

Basically `clojars.scp` implements the [SCP protocol](http://blogs.sun.com/janp/entry/how_the_scp_protocol_works)
and acts as a Nailgun "nail" so it can be called from the
command-line.  Clojars writes an SSH `authorized_keys` file
with a line for each user's public key setting the login command to
`ng --nailgun-port 8700 clojars.scp USERNAME`.

To set it up:

1. Install the Nailgun `ng` C program.  Just unpack the Nailgun source
and run `make` then copy the `ng` executable somewhere like `/usr/local/bin`

2. Create a "clojars" unix account with a disabled password.

3. Disable password authentication for that user by adding this to
`/etc/ssh/sshd_config`:

        Match User clojars
        PasswordAuthentication no

4. Symlink in the auth_keys file the webapp generates:

        cd /home/clojars
        mkdir .ssh
        cd .ssh
        ln -s ...../clojars-web/data/authorized_keys authorized_keys

Mailing lists
-------------

There's a public mailing list
[clojars@googlegroups.com](http://groups.google.com/group/clojars) for
general discussion.  There's also a
[separate maintainers list](http://groups.google.com/group/clojars-maintainers)
for people looking after the repository and server.
