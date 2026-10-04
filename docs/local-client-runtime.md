# Local client builds

Use `ant run` to build and start the local client. Each launch uses a complete
private copy of the JARs and server configuration in `.client-runtimes/client-*`.
Existing windows keep their original classes and resources when another client
is built or started. The copy is removed after that client exits. `ant runtime`
prepares a copy without opening a window and keeps it for later use.

The client JAR is compiled from fresh class files, so an API rollback cannot
package callers left over from an earlier incremental build.

`ant bin` updates the installed runtime only when no Java client is using `bin`.
`ant clean` has the same check. Both stop with the affected process IDs rather
than changing files underneath an existing client. Close those windows first;
`ant run` works while they remain open.

Runtime copies survive `ant clean`. Remove an old copy only after its client
has exited. Do not modify the JARs in a running client's copy.
