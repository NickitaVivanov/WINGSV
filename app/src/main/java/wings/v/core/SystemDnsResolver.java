package wings.v.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.InetAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Reads DNS servers from the device's current physical (non-VPN) network.
 */
@SuppressWarnings(
    {
        "PMD.AvoidCatchingGenericException",
        "PMD.CommentRequired",
        "PMD.MethodArgumentCouldBeFinal",
        "PMD.LocalVariableCouldBeFinal",
        "PMD.OnlyOneReturn",
        "PMD.LawOfDemeter",
    }
)
public final class SystemDnsResolver {

    private SystemDnsResolver() {}

    @NonNull
    public static List<String> getServers(@NonNull Context context) {
        LinkedHashSet<String> servers = new LinkedHashSet<>();
        ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) {
            return List.of();
        }
        Network network = DirectNetworkConnection.findUsablePhysicalNetwork(context);
        if (network == null) {
            return List.of();
        }
        try {
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            if (linkProperties == null) {
                return List.of();
            }
            List<InetAddress> dnsServers = linkProperties.getDnsServers();
            if (dnsServers == null) {
                return List.of();
            }
            for (InetAddress address : dnsServers) {
                if (address == null || address.isLoopbackAddress()) {
                    continue;
                }
                String host = trim(address.getHostAddress());
                if (!TextUtils.isEmpty(host)) {
                    servers.add(host);
                }
            }
        } catch (RuntimeException ignored) {}
        return new ArrayList<>(servers);
    }

    @NonNull
    public static String joinComma(@NonNull Context context) {
        return TextUtils.join(", ", getServers(context));
    }

    @NonNull
    public static String firstDialTarget(@NonNull Context context) {
        List<String> servers = getServers(context);
        if (servers.isEmpty()) {
            return "";
        }
        return toUdpDialTarget(servers.get(0));
    }

    @NonNull
    public static String requireJoinComma(@NonNull Context context) {
        String joined = joinComma(context);
        if (TextUtils.isEmpty(joined)) {
            throw new IllegalStateException(
                "Системный DNS недоступен: не удалось получить DNS-серверы с активной сети устройства"
            );
        }
        return joined;
    }

    @NonNull
    private static String toUdpDialTarget(@NonNull String host) {
        String normalized = trim(host);
        if (TextUtils.isEmpty(normalized)) {
            return "";
        }
        if (normalized.contains(":") && !normalized.startsWith("[")) {
            return "[" + normalized + "]:53";
        }
        if (normalized.startsWith("[")) {
            return normalized.contains("]:") ? normalized : normalized + ":53";
        }
        int colon = normalized.indexOf(':');
        if (colon > 0 && colon == normalized.lastIndexOf(':')) {
            String port = normalized.substring(colon + 1);
            if (isDigits(port)) {
                return normalized;
            }
        }
        return normalized + ":53";
    }

    private static boolean isDigits(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
