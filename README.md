This executable application performs:
  1. Export of Integrity metadata as json files (<integrityid>.json)
  2. Stores partial information to oracle database table, migrationdata for improving performance of post migration steps and validations

Usage:
update `jdbc.properties` and `mks.properties` files

execute `java -jar IntegrityJamaPremigrator.jar`

Note:
Java 17+ needed to run the application
