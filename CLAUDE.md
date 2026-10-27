# Pulso Spring Boot Starter

Starter open source (Apache 2.0) que reporta los métodos `@Monitored` a Pulso. Ver README.md.

## Reglas

- La regla de oro: **el starter nunca afecta al job del cliente**. Nada lanza excepciones hacia el job, nada bloquea su hilo, y las excepciones del job se propagan igual que sin `@Monitored`.
- Compatible con Spring Boot 3.5 y 4.x, bytecode Java 17: solo APIs presentes en ambas. Validar con `./mvnw verify` y `./mvnw verify -Pboot4` antes de subir cambios.
- Sin dependencias nuevas para el cliente: nada de Jackson ni SLF4J directo (logging con commons-logging de Spring, JSON a mano en `MonitorRegistration`).
- El cliente envía en un solo hilo y en orden: el registro de un monitor siempre sale antes de su primer ping, y el inicio antes que el fin.
- El README va en inglés (comunidad Java global); los commits y la documentación interna, en español.
