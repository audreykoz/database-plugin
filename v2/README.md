# Archimate Tool Database-Plugin
Database export and import plugin that store models in a central repository.

## Archi versions compatibility
* The v2 of the plugin is compatible with Archi 4 only.

## The current version is able to:
* Export and import models to a relational database (PostgreSQL, MySQL, MS SQL Server, Oracle and SQLite drivers are included)
* Export elements and relationships to a graph database (Neo4J driver is included)
* Version the models (keep a history on the model versions so you can retrieve a former version)
* Version models components (keep an history on the components versions so you can retrieve a former version)
* Share elements, relationships, and views between models
* Automatically update when a new plugin release is available on GitHub
* Automatically create and update database tables when necessary

## Installation instructions:
The plugin installation must be done manually:
* download the **org.archicontribs.database_v2.xxx.jar** file to the Archi **plugins** folder
* download the **sqljdbc_auth.dll** file to the Archi **JRE\bin** folder if you plan to use MSQ SQL integrated security mode (i.e. Windows authentication)
* start (or restart) Archi and the *import from database*, *export to database* menu entries and the *Database plugin* preferences page should be visible ...

The following updates can then be done using the "check for update" button in the preferences page.

## Key Differences from v1:
* Added log4j support
* Versioning at the element level
* Reduce the quantity of data exported by exporting only updated components (use of checksums)
* Detect database conflicts and add a conflict resolution mechanism
* Reduce number of database tables
* Reduce database table name length to be compliant will all database brands
* Add back Oracle JDBC driver
* Complete rework of the graphical interface
* Add the ability to import components from other models
* Add inline help

## Wiki
Please do not hesitate to have a look at the [Wiki](https://github.com/archi-contribs/database-plugin/wiki).