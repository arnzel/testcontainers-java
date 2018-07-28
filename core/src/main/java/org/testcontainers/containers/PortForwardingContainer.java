package org.testcontainers.containers;

import com.github.dockerjava.api.model.ContainerNetwork;
import com.trilead.ssh2.Connection;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public enum PortForwardingContainer {
    INSTANCE;

    private GenericContainer container;

    private final Set<Integer> exposedPorts = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Getter(value = AccessLevel.PRIVATE, lazy = true)
    private final Connection sshConnection = createSSHSession();

    @SneakyThrows
    private Connection createSSHSession() {
        container = new GenericContainer<>(TestcontainersConfiguration.getInstance().getSSHdImage())
            .withExposedPorts(22)
            .withCommand(
                "/usr/sbin/sshd",
                "-D",
                "-o", "PermitRootLogin=yes",
                // Disable ipv6
                "-o", "AddressFamily=inet",
                // Make it listen on all interfaces, not just localhost
                "-o", "GatewayPorts=yes"
            );
        container.start();

        Connection connection = new Connection(container.getContainerIpAddress(), container.getMappedPort(22));

        connection.setTCPNoDelay(true);
        connection.connect(
            (hostname, port, serverHostKeyAlgorithm, serverHostKey) -> true,
            (int) Duration.ofSeconds(30).toMillis(),
            (int) Duration.ofSeconds(30).toMillis()
        );

        if (!connection.authenticateWithPassword("root", "root")) {
            throw new IllegalStateException("Authentication failed.");
        }

        return connection;
    }

    @SneakyThrows
    public void exposeHostPort(int port) {
        if (exposedPorts.add(port)) {
            getSshConnection().requestRemotePortForwarding("", port, "localhost", port);
        }
    }

    Optional<ContainerNetwork> getNetwork() {
        return Optional.ofNullable(container)
            .map(GenericContainer::getContainerInfo)
            .flatMap(it -> it.getNetworkSettings().getNetworks().values().stream().findFirst());
    }
}