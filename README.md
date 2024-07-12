# Fake JDBC driver to read configuration files

This is a workaround to compensate for the lack of a TOML/YAML/... Config Source in Payara.
Using a fake JDBC driver, we can leverage the [JDBC Config Source][payara-docs-jdbc] already [present at early initialization stages][payara-code-add-jdbc].
Any Config Source provided by an application is late to the party for any [variable substitution using `${MPCONFIG=...}`][payara-docs-var-ref].

**IMPORTANT**: THIS IS EXPERIMENTAL AND STILL UNDER CONSTRUCTION. USE AT YOUR OWN RISK AND PERIL.

## Attribution

Thank you, @TheElectronWill for [night-config](https://github.com/TheElectronWill/night-config), the LGPLv3 licensed TOML lib we use. The library is shaded into the JAR to allow single-file distribution and usage as well as avoiding classpath conflicts.

## Notes

### TODO

- Look into using Jackson instead of night-config (more lightweight, larger maintainer community, core libs already present)
- Add more tests
- Add more logging options
- Decide how to deal with a non-existing file (fail or ignore). Maybe make it configurable via JDBC property and/or URL parameter?

### Upstream
There is an inquiry to add a TOML Config Source to upstream: [payara/payara#6822](https://github.com/payara/Payara/issues/6822)

### Usage

**IMPORTANT**: For now, this driver supports only reading [TOML](https://toml.io/en/v1.0.0) files.

1. After packaging (`mvn package`), put the JAR under `glassfish/lib` folder (see also [Class Loading][payara-docs-classloading])
2. You should restart your appserver now to make it pick up the library.
3. Create a JDBC connection and configure the Config Source.
    ```shell
    asadmin create-jdbc-connection-pool --restype java.sql.Driver --driverclassname io.gdcc.jdbc.conffile.ConfFileDriver confFilePool
    # Workaround because of defunct colon escaping in create command...
    asadmin set resources.jdbc-connection-pool.confFilePool.property.url='jdbc:conffile:toml://${ENV=CONFIG_DIR}'
    asadmin create-jdbc-resource --connectionpoolid confFilePool java/db/conffile
    asadmin set-jdbc-config-source-configuration --jndiname "java/db/conffile" --tablename "dataverse" --keycolumnname "key" --valuecolumnname "value"
    ```

Hints:
- You can replace the `${ENV=CONFIG_DIR}` part with a path or other env var name, just let the (resolved) value point to a Payara user readable directory.
- The table name will determine the files' basename. Using the example configuration above, the driver will try to read from the file at `${CONFIG_DIR}/dataverse.toml`.
- Profiles are supported, too. Just provide a file `${CONFIG_DIR}/<table name>-<profile>.toml`.

### Caching
Payara caches values, by [default for 60s](https://github.com/payara/Payara/blob/1411893e1db88eef9155496ee0c06477ffd3a67e/nucleus/payara-modules/nucleus-microprofile/config-service/src/main/java/fish/payara/nucleus/microprofile/config/spi/MicroprofileConfigConfiguration.java#L129).

The consequence: when you change the TOML file, they will not be picked up until `<duration>` seconds have passed since they have last been read from the same file. Keep your cool with the edits.

Cache timeout can be configured in two ways:

1. Use `asadmin set-config-cache --duration=x`, where x is in seconds. This is a live change!
2. Provide a value for the key `mp.config.cache.duration` in any early read MP config source. This is a near-live change depending on the source you use (e.g. env vars cannot easily be changed after Payara has started).


[payara-docs-jdbc]: https://docs.payara.fish/community/docs/Technical%20Documentation/MicroProfile/Config/JDBC.html
[payara-docs-var-ref]: https://docs.payara.fish/community/docs/Technical%20Documentation/Payara%20Server%20Documentation/General%20Administration/Configuration%20Variables%20Reference.html#references-to-microprofile-properties
[payara-docs-classloading]: https://docs.payara.fish/community/docs/Technical%20Documentation/Application%20Development/Class%20Loaders.html#common-libraries
[payara-code-add-jdbc]: https://github.com/payara/Payara/blob/1411893e1db88eef9155496ee0c06477ffd3a67e/nucleus/payara-modules/nucleus-microprofile/config-service/src/main/java/fish/payara/nucleus/microprofile/config/spi/ConfigProviderResolverImpl.java#L345