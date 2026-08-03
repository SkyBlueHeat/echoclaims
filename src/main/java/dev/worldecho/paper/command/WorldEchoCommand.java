package dev.worldecho.paper.command;

import dev.worldecho.application.BindingEnricher;
import dev.worldecho.application.ItemValueScorer;
import dev.worldecho.config.WorldEchoSettings;
import dev.worldecho.domain.binding.BindingType;
import dev.worldecho.domain.binding.ContentBinding;
import dev.worldecho.domain.binding.EnrichedContent;
import dev.worldecho.domain.content.ContentKey;
import dev.worldecho.domain.content.IdentifiedContent;
import dev.worldecho.domain.scenario.EligibilityCatalog;
import dev.worldecho.domain.scenario.EligibilityEvaluator;
import dev.worldecho.domain.scenario.EligibilityFormatter;
import dev.worldecho.domain.scenario.EligibilityProfile;
import dev.worldecho.domain.scenario.EligibilityResult;
import dev.worldecho.domain.item.ItemDescriptor;
import dev.worldecho.domain.item.ItemScore;
import dev.worldecho.domain.item.OwnershipState;
import dev.worldecho.domain.item.ReconciliationMetrics;
import dev.worldecho.domain.item.TrackedItemId;
import dev.worldecho.domain.item.TrackedItemRecord;
import dev.worldecho.domain.memory.StoryMemoryEvent;
import dev.worldecho.integration.IntegrationRegistry;
import dev.worldecho.integration.bukkit.BukkitItems;
import dev.worldecho.paper.message.PaperMessageService;
import dev.worldecho.persistence.DatabaseManager;
import dev.worldecho.persistence.StoryEventRepository;
import dev.worldecho.persistence.StoryWriteQueue;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import dev.worldecho.paper.inventory.PlayerInventoryReconciliationScheduler;
import dev.worldecho.paper.item.ItemIdentityAdapter;
import dev.worldecho.persistence.OwnershipLedgerRepository;
import dev.worldecho.persistence.TrackedItemRepository;

/**
 * Administration command. Database access always runs on the WorldEcho query executor and
 * results are sent back on the server thread.
 */
public final class WorldEchoCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "worldecho.admin";

    private static final double ENTITY_TRACE_DISTANCE = 12.0d;
    private static final List<String> SUBCOMMANDS =
            List.of("status", "recent", "inspect", "reload", "eligibility", "item");
    private static final List<String> INSPECT_TARGETS = List.of("item", "entity");
    private static final List<String> ELIGIBILITY_SUBCOMMANDS =
            List.of("profiles", "check", "all");
    private static final List<String> ELIGIBILITY_TARGETS = List.of("entity", "item");
    private static final int ELIGIBILITY_ALL_LIMIT = 20;

    private final EligibilityCatalog eligibilityCatalog = EligibilityCatalog.builtin();
    private final EligibilityEvaluator eligibilityEvaluator =
            new EligibilityEvaluator(eligibilityCatalog);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final Supplier<WorldEchoSettings> settingsSupplier;
    private final Supplier<PaperMessageService> messageSupplier;
    private final Supplier<ItemValueScorer> scorerSupplier;
    private final Supplier<BindingEnricher> enricherSupplier;
    private final IntegrationRegistry integrations;
    private final DatabaseManager databaseManager;
    private final StoryEventRepository repository;
    private final StoryWriteQueue writeQueue;
    private final Executor queryExecutor;
    private final Supplier<List<String>> reloadAction;
    private final TrackedItemRepository trackedItemRepository;
    private final OwnershipLedgerRepository ledgerRepository;
    private final ItemIdentityAdapter identityAdapter;
    private final ReconciliationMetrics reconciliationMetrics;
    private final ItemCommandHandler itemHandler;

    public WorldEchoCommand(
            Plugin plugin,
            Supplier<WorldEchoSettings> settingsSupplier,
            Supplier<PaperMessageService> messageSupplier,
            Supplier<ItemValueScorer> scorerSupplier,
            Supplier<BindingEnricher> enricherSupplier,
            IntegrationRegistry integrations,
            DatabaseManager databaseManager,
            StoryEventRepository repository,
            StoryWriteQueue writeQueue,
            Executor queryExecutor,
            Supplier<List<String>> reloadAction,
            ItemIdentityAdapter identityAdapter,
            TrackedItemRepository trackedItemRepository,
            OwnershipLedgerRepository ledgerRepository,
            PlayerInventoryReconciliationScheduler reconciliationScheduler,
            ReconciliationMetrics reconciliationMetrics
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.messageSupplier = Objects.requireNonNull(messageSupplier, "messageSupplier");
        this.scorerSupplier = Objects.requireNonNull(scorerSupplier, "scorerSupplier");
        this.enricherSupplier = Objects.requireNonNull(enricherSupplier, "enricherSupplier");
        this.integrations = Objects.requireNonNull(integrations, "integrations");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.writeQueue = Objects.requireNonNull(writeQueue, "writeQueue");
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
        this.trackedItemRepository = Objects.requireNonNull(trackedItemRepository, "trackedItemRepository");
        this.ledgerRepository = Objects.requireNonNull(ledgerRepository, "ledgerRepository");
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
        this.reconciliationMetrics = Objects.requireNonNull(reconciliationMetrics, "reconciliationMetrics");
        this.itemHandler = new ItemCommandHandler(
                plugin,
                settingsSupplier,
                messageSupplier,
                scorerSupplier,
                enricherSupplier,
                integrations,
                identityAdapter,
                trackedItemRepository,
                ledgerRepository,
                queryExecutor,
                reconciliationScheduler,
                reconciliationMetrics
        );
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            messages().send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            messages().send(sender, "usage");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "recent" -> recent(sender, args);
            case "inspect" -> inspect(sender, args);
            case "reload" -> reload(sender);
            case "eligibility" -> eligibility(sender, args);
            case "item" -> itemHandler.handle(sender, args);
            default -> messages().send(sender, "unknown-subcommand");
        }

        return true;
    }

    private void status(CommandSender sender) {
        WorldEchoSettings settings = settingsSupplier.get();
        StoryWriteQueue.QueueStatus queue = writeQueue.status();

        messages().send(sender, "status-header");
        line(sender, "version", plugin.getPluginMeta().getVersion());
        line(sender, "locale", settings.locale());
        line(sender, "queue.pending", Integer.toString(queue.pending()));
        line(sender, "queue.written", Long.toString(queue.written()));
        line(sender, "queue.failed", Long.toString(queue.failed()));
        line(sender, "queue.dropped", Long.toString(queue.dropped()));
        line(sender, "providers", String.join(", ", integrations.describeProviders()));

        BindingEnricher enricher = enricherSupplier.get();
        line(sender, "bindings.entities", Integer.toString(enricher.registry().entityBindingCount()));
        line(sender, "bindings.items", Integer.toString(enricher.registry().itemBindingCount()));
        line(sender, "bindings.warnings", Long.toString(enricher.registry().warningCount()));
        line(sender, "bindings.errors", Long.toString(enricher.registry().errorCount()));
        line(sender, "bindings.schema-version", Integer.toString(enricher.registry().schemaVersion()));
        line(sender, "eligibility.profiles", Integer.toString(eligibilityCatalog.size()));
        line(sender, "eligibility.entity-profiles", Integer.toString(eligibilityCatalog.entityProfileCount()));
        line(sender, "eligibility.item-profiles", Integer.toString(eligibilityCatalog.itemProfileCount()));

        query(
                sender,
                () -> new DatabaseStatus(
                        databaseManager.healthy(),
                        databaseManager.schemaVersion(),
                        repository.count(),
                        trackedItemRepository.count(),
                        ledgerRepository.count()
                ),
                status -> {
                    line(sender, "database", status.healthy() ? "ok" : "unavailable");
                    line(sender, "schema.version", Integer.toString(status.schemaVersion()));
                    line(sender, "events", Long.toString(status.events()));
                    line(sender, "tracked-items", Long.toString(status.trackedItems()));
                    line(sender, "ledger-entries", Long.toString(status.ledgerEntries()));
                },
                "status-failed"
        );

        line(sender, "auto-tracking", settings.automaticTrackingEnabled() ? "enabled" : "disabled");
        line(sender, "recon.count", Long.toString(reconciliationMetrics.inventoryReconciliations()));
        line(sender, "recon.identities", Long.toString(reconciliationMetrics.automaticIdentitiesAssigned()));
        line(sender, "recon.lots", Long.toString(reconciliationMetrics.automaticLotsAssigned()));
        line(sender, "recon.ownership", Long.toString(reconciliationMetrics.ownershipTransitionsRecorded()));
        line(sender, "recon.warnings", Long.toString(reconciliationMetrics.identityWarnings()));
        line(sender, "recon.duplicates", Long.toString(reconciliationMetrics.duplicateIdentityObservations()));
        line(sender, "recon.pending", Long.toString(reconciliationMetrics.pendingReconciliations()));
    }

    private void recent(CommandSender sender, String[] args) {
        WorldEchoSettings settings = settingsSupplier.get();
        int requested = settings.recentDefaultCount();

        if (args.length >= 2) {
            try {
                requested = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                messages().send(sender, "recent-invalid-count",
                        Map.of("value", args[1], "default", Integer.toString(requested)));
            }
        }

        int count = Math.max(1, Math.min(requested, settings.recentMaximumCount()));
        messages().send(sender, "recent-loading", Map.of("count", Integer.toString(count)));

        query(
                sender,
                () -> repository.findRecent(count),
                events -> showRecent(sender, events),
                "recent-failed"
        );
    }

    private void showRecent(CommandSender sender, List<StoryMemoryEvent> events) {
        messages().send(sender, "recent-header");
        if (events.isEmpty()) {
            messages().send(sender, "recent-empty");
            return;
        }

        for (StoryMemoryEvent event : events) {
            messages().send(sender, "recent-line", Map.of(
                    "time", TIME_FORMATTER.format(event.occurredAt()),
                    "type", event.type().name(),
                    "actor", event.actor().toString(),
                    "item", event.optionalItem().map(Object::toString).orElse("-"),
                    "id", event.id().toString().substring(0, 8)
            ));
        }
    }

    private void inspect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "players-only");
            return;
        }

        if (args.length < 2) {
            messages().send(sender, "inspect-usage");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "item" -> inspectItem(player);
            case "entity" -> inspectEntity(player);
            default -> messages().send(sender, "inspect-usage");
        }
    }

    private void inspectItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            messages().send(player, "inspect-no-item");
            return;
        }

        Optional<IdentifiedContent> identified = integrations.identifyItem(item);
        if (identified.isEmpty()) {
            messages().send(player, "inspect-no-item");
            return;
        }

        BindingEnricher enricher = enricherSupplier.get();
        EnrichedContent enriched = enricher.enrichItem(identified.get());

        ItemDescriptor descriptor =
                BukkitItems.describe(item, identified.get().key().providerId());
        ItemScore score = scorerSupplier.get().score(descriptor);

        showContent(player, enriched);
        line(player, "material", descriptor.materialKey());
        line(player, "score", Integer.toString(score.value()));
        line(player, "score.factors", score.explain());
        line(player, "minimum-score",
                Integer.toString(settingsSupplier.get().minimumItemScore()));

        ItemIdentityAdapter.IdentityResult identity = identityAdapter.readIdentity(item);
        switch (identity.status()) {
            case UNSUPPORTED_ITEM -> line(player, "worldecho.item-id", "untracked");
            case MALFORMED -> messages().send(player, "item-malformed-id");
            case MISSING -> line(player, "worldecho.item-id", "untracked");
            case EXISTING -> {
                TrackedItemId itemId = identity.itemId();
                line(player, "worldecho.item-id", itemId.toString());
                queryExecutor.execute(() -> {
                    try {
                        Optional<TrackedItemRecord> record = trackedItemRepository.findById(itemId);
                        Optional<OwnershipState> state = ledgerRepository.findCurrentOwnership(itemId);
                        long historyCount = ledgerRepository.countHistory(itemId);
                        runOnServerThread(() -> {
                            if (record.isEmpty()) {
                                messages().send(player, "item-persistence-missing",
                                        Map.of("item-id", itemId.toString()));
                            }
                            if (state.isPresent()) {
                                OwnershipState s = state.get();
                                line(player, "worldecho.current-owner",
                                        s.optionalCurrentSubject()
                                                .map(os -> os.describe()).orElse("-"));
                                line(player, "worldecho.history-count",
                                        Long.toString(historyCount));
                            } else {
                                line(player, "worldecho.current-owner", "-");
                                line(player, "worldecho.history-count", "0");
                            }
                        });
                    } catch (Exception exception) {
                        plugin.getLogger().log(Level.WARNING, "Inspect item ownership query failed", exception);
                    }
                });
            }
        }
    }

    private void inspectEntity(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                ENTITY_TRACE_DISTANCE,
                entity -> !entity.getUniqueId().equals(player.getUniqueId())
        );

        Entity entity = result == null ? null : result.getHitEntity();
        if (entity == null) {
            messages().send(player, "inspect-no-entity");
            return;
        }

        Optional<IdentifiedContent> identified = integrations.identifyEntity(entity);
        if (identified.isEmpty()) {
            messages().send(player, "inspect-no-entity");
            return;
        }

        BindingEnricher enricher = enricherSupplier.get();
        EnrichedContent enriched = enricher.enrichEntity(identified.get());

        showContent(player, enriched);
        line(player, "runtime-id", entity.getUniqueId().toString());
    }

    private void showContent(CommandSender sender, EnrichedContent content) {
        messages().send(sender, "inspect-header");
        line(sender, "key", content.key().toString());
        line(sender, "display", content.displayName());
        line(sender, "roles", describe(content.roles()));
        line(sender, "capabilities", describe(content.capabilities()));
        content.optionalBinding().ifPresent(binding -> {
            line(sender, "faction", binding.optionalFaction().orElse("-"));
            line(sender, "rank", binding.optionalRank().orElse("-"));
            line(sender, "superior", binding.optionalSuperior().map(Object::toString).orElse("-"));
            line(sender, "tags", binding.tags().isEmpty() ? "-" : String.join(", ", binding.tags()));
        });
        showEligibilitySummary(sender, content);
    }

    private void showEligibilitySummary(CommandSender sender, EnrichedContent content) {
        BindingType type = content.optionalBinding()
                .map(ContentBinding::type).orElse(null);
        if (type == null) {
            return;
        }
        List<EligibilityProfile> profiles = eligibilityCatalog.profilesFor(type);
        if (profiles.isEmpty()) {
            return;
        }
        List<String> summaries = new ArrayList<>();
        for (EligibilityProfile profile : profiles) {
            EligibilityResult result = eligibilityEvaluator.evaluate(content, profile.id());
            summaries.add(EligibilityFormatter.formatSummary(result));
        }
        line(sender, "eligibility", String.join("; ", summaries));
    }

    private void eligibility(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "eligibility-usage");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "profiles" -> eligibilityProfiles(sender);
            case "check" -> eligibilityCheck(sender, args);
            case "all" -> eligibilityAll(sender, args);
            default -> messages().send(sender, "eligibility-usage");
        }
    }

    private void eligibilityProfiles(CommandSender sender) {
        messages().send(sender, "eligibility-profiles-header");
        for (String line : EligibilityFormatter.formatProfiles(eligibilityCatalog)) {
            messages().send(sender, "eligibility-profile-line", Map.of("line", line));
        }
    }

    private void eligibilityCheck(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messages().send(sender, "eligibility-check-usage");
            return;
        }

        String targetType = args[2].toLowerCase(Locale.ROOT);
        String contentKeyStr = args[3];
        String profileId = args[4];

        BindingType type = parseBindingType(targetType);
        if (type == null) {
            messages().send(sender, "eligibility-invalid-target");
            return;
        }

        ContentKey contentKey;
        try {
            contentKey = ContentKey.parse(contentKeyStr);
        } catch (IllegalArgumentException e) {
            messages().send(sender, "eligibility-invalid-key",
                    Map.of("key", sanitize(contentKeyStr)));
            return;
        }

        BindingEnricher enricher = enricherSupplier.get();
        ContentBinding binding = type == BindingType.ENTITY
                ? enricher.registry().findEntityBinding(contentKey).orElse(null)
                : enricher.registry().findItemBinding(contentKey).orElse(null);

        EligibilityResult result;
        if (binding == null) {
            result = new dev.worldecho.domain.scenario.EligibilityResult(
                    profileId, contentKey, null,
                    dev.worldecho.domain.scenario.EligibilityStatus.NOT_ELIGIBLE,
                    java.util.Set.of(), java.util.Set.of(),
                    java.util.Set.of(), java.util.Set.of(),
                    java.util.List.of(), java.util.Set.of(),
                    java.util.List.of(new dev.worldecho.domain.scenario.EligibilityDiagnostic(
                            dev.worldecho.domain.scenario.EligibilityDiagnosticCode.NO_BINDING,
                            profileId, contentKey, "binding",
                            "No binding found for " + contentKey
                    ))
            );
        } else {
            result = eligibilityEvaluator.evaluate(binding, profileId);
        }

        for (String line : EligibilityFormatter.formatResult(result)) {
            messages().send(sender, "eligibility-result-line", Map.of("line", line));
        }
    }

    private void eligibilityAll(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages().send(sender, "eligibility-all-usage");
            return;
        }

        String targetType = args[2].toLowerCase(Locale.ROOT);
        String contentKeyStr = args[3];

        BindingType type = parseBindingType(targetType);
        if (type == null) {
            messages().send(sender, "eligibility-invalid-target");
            return;
        }

        ContentKey contentKey;
        try {
            contentKey = ContentKey.parse(contentKeyStr);
        } catch (IllegalArgumentException e) {
            messages().send(sender, "eligibility-invalid-key",
                    Map.of("key", sanitize(contentKeyStr)));
            return;
        }

        BindingEnricher enricher = enricherSupplier.get();
        ContentBinding binding = type == BindingType.ENTITY
                ? enricher.registry().findEntityBinding(contentKey).orElse(null)
                : enricher.registry().findItemBinding(contentKey).orElse(null);

        messages().send(sender, "eligibility-all-header",
                Map.of("key", contentKey.toString()));

        List<EligibilityProfile> profiles = eligibilityCatalog.profilesFor(type);
        int count = 0;
        for (EligibilityProfile profile : profiles) {
            if (count >= ELIGIBILITY_ALL_LIMIT) {
                messages().send(sender, "eligibility-all-limit",
                        Map.of("limit", Integer.toString(ELIGIBILITY_ALL_LIMIT)));
                break;
            }
            EligibilityResult result;
            if (binding == null) {
                result = new dev.worldecho.domain.scenario.EligibilityResult(
                        profile.id(), contentKey, null,
                        dev.worldecho.domain.scenario.EligibilityStatus.NOT_ELIGIBLE,
                        java.util.Set.of(), java.util.Set.of(),
                        java.util.Set.of(), java.util.Set.of(),
                        java.util.List.of(), java.util.Set.of(),
                        java.util.List.of(new dev.worldecho.domain.scenario.EligibilityDiagnostic(
                                dev.worldecho.domain.scenario.EligibilityDiagnosticCode.NO_BINDING,
                                profile.id(), contentKey, "binding",
                                "No binding found for " + contentKey
                        ))
                );
            } else {
                result = eligibilityEvaluator.evaluate(binding, profile.id());
            }
            messages().send(sender, "eligibility-result-line",
                    Map.of("line", EligibilityFormatter.formatSummary(result)));
            count++;
        }
    }

    private static BindingType parseBindingType(String value) {
        return switch (value) {
            case "entity" -> BindingType.ENTITY;
            case "item" -> BindingType.ITEM;
            default -> null;
        };
    }

    private static String sanitize(String input) {
        return input.replaceAll("[&<>\\u00a7\\n\\r]", "");
    }

    private void reload(CommandSender sender) {
        try {
            List<String> warnings = reloadAction.get();
            messages().send(sender, "reload-success");
            for (String warning : warnings) {
                messages().send(sender, "reload-warning", Map.of("warning", warning));
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "WorldEcho configuration reload failed", exception);
            messages().send(sender, "reload-failed");
        }
    }

    /**
     * Runs a blocking database call on the query executor and delivers the result on the
     * server thread.
     */
    private <T> void query(
            CommandSender sender,
            ThrowingSupplier<T> supplier,
            Consumer<T> consumer,
            String failureKey
    ) {
        queryExecutor.execute(() -> {
            T value = null;
            Exception failure = null;
            try {
                value = supplier.get();
            } catch (Exception exception) {
                failure = exception;
            }

            T result = value;
            Exception thrown = failure;
            runOnServerThread(() -> {
                if (thrown != null) {
                    plugin.getLogger().log(Level.WARNING, "WorldEcho query failed", thrown);
                    messages().send(sender, failureKey);
                    return;
                }
                consumer.accept(result);
            });
        });
    }

    private void runOnServerThread(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    private void line(CommandSender sender, String key, String value) {
        messages().send(sender, "status-line", Map.of("key", key, "value", value));
    }

    private PaperMessageService messages() {
        return messageSupplier.get();
    }

    private static String describe(Collection<? extends Enum<?>> values) {
        if (values.isEmpty()) {
            return "-";
        }
        return values.stream().map(Enum::name).sorted().reduce((a, b) -> a + ", " + b).orElse("-");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("inspect")) {
            return filter(INSPECT_TARGETS, args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("recent")) {
            WorldEchoSettings settings = settingsSupplier.get();
            return filter(
                    List.of(
                            Integer.toString(settings.recentDefaultCount()),
                            Integer.toString(settings.recentMaximumCount())
                    ),
                    args[1]
            );
        }

        if (args[0].equalsIgnoreCase("eligibility")) {
            return eligibilityTabComplete(args);
        }

        if (args[0].equalsIgnoreCase("item")) {
            return itemHandler.tabComplete(args);
        }

        return List.of();
    }

    private List<String> eligibilityTabComplete(String[] args) {
        if (args.length == 2) {
            return filter(ELIGIBILITY_SUBCOMMANDS, args[1]);
        }

        if (args.length == 3 && (args[1].equalsIgnoreCase("check")
                || args[1].equalsIgnoreCase("all"))) {
            return filter(ELIGIBILITY_TARGETS, args[2]);
        }

        if (args.length == 4 && (args[1].equalsIgnoreCase("check")
                || args[1].equalsIgnoreCase("all"))) {
            BindingEnricher enricher = enricherSupplier.get();
            String typeArg = args[2].toLowerCase(Locale.ROOT);
            List<String> keys = new ArrayList<>();
            if (typeArg.equals("entity")) {
                enricher.registry().entityBindings().values()
                        .forEach(b -> keys.add(b.key().toString()));
            } else if (typeArg.equals("item")) {
                enricher.registry().itemBindings().values()
                        .forEach(b -> keys.add(b.key().toString()));
            }
            return filter(keys, args[3]);
        }

        if (args.length == 5 && args[1].equalsIgnoreCase("check")) {
            String typeArg = args[2].toLowerCase(Locale.ROOT);
            List<String> profileIds = new ArrayList<>();
            for (EligibilityProfile p : eligibilityCatalog.allProfiles()) {
                if (typeArg.equals("entity") && p.bindingType() == BindingType.ENTITY) {
                    profileIds.add(p.id());
                } else if (typeArg.equals("item") && p.bindingType() == BindingType.ITEM) {
                    profileIds.add(p.id());
                }
            }
            return filter(profileIds, args[4]);
        }

        return List.of();
    }

    static List<String> filter(List<String> candidates, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(normalized)) {
                matches.add(candidate);
            }
        }
        return List.copyOf(matches);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private record DatabaseStatus(boolean healthy, int schemaVersion, long events, long trackedItems, long ledgerEntries) {
    }
}
