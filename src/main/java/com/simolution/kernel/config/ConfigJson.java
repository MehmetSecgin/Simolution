package com.simolution.kernel.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

public final class ConfigJson {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = InflowSource.Disk.class, name = "disk"),
            @JsonSubTypes.Type(value = InflowSource.Rect.class, name = "rect"),
            @JsonSubTypes.Type(value = InflowSource.Points.class, name = "points"),
            @JsonSubTypes.Type(value = InflowSource.Noise.class, name = "noise"),
            @JsonSubTypes.Type(value = InflowSource.Ring.class, name = "ring")
    })
    private interface InflowSourceMixin {

        @JsonIgnore
        boolean isStatic();
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .addMixIn(InflowSource.class, InflowSourceMixin.class)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final ObjectMapper STRICT_MAPPER = MAPPER.copy()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);

    private static final DefaultPrettyPrinter PRINTER =
            new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n"));

    private record FlatEngineConfig(
            long seed,
            int worldWidth,
            int maxGenes,
            List<ChemicalSpec> chemicals,
            double initialEnergy,
            double basalCost,
            double costPerPropagation,
            double harvestCapacityPerConnection,
            double harvestHalfSaturation,
            double harvestMassExponent,
            double moveCostBase,
            double moveCostPerMass,
            double moveDeadzone,
            double selfEnergyScale,
            double selfMassScale,
            double initialMass,
            double maintPerMass,
            double growMax,
            double growHalfSaturation,
            double growYield,
            double buildCost,
            double buildCostPerGene,
            double mutationRateWeight,
            double mutationRateStruct,
            double indelRateDup,
            double indelRateDel,
            double agingCost
    ) {}

    private ConfigJson() {
    }

    public static String write(final EngineConfig config) {
        try {
            return MAPPER.writer(PRINTER).writeValueAsString(flatten(config)) + "\n";
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("cannot serialize config: " + e.getOriginalMessage(), e);
        }
    }

    public static EngineConfig read(final String json) {
        try {
            rejectUnknownKeys(json);
            return readStrict(json);
        } catch (final ValueInstantiationException e) {
            final Throwable cause = e.getCause() instanceof IllegalArgumentException ? e.getCause() : e;
            throw new IllegalArgumentException(
                    "invalid config: " + (cause == e ? e.getOriginalMessage() : cause.getMessage()), e);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("invalid config: " + e.getOriginalMessage(), e);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid config: " + e.getMessage(), e);
        }
    }

    // A misspelt key must be named before the missing key it leaves behind, and a missing key must
    // surface as the validation failure its default value causes, so this lenient pass comes first.
    private static void rejectUnknownKeys(final String json) throws JsonProcessingException {
        final FlatEngineConfig flat = MAPPER.readValue(json, FlatEngineConfig.class);
        if (flat.chemicals() != null) {
            inflate(flat);
        }
    }

    private static EngineConfig readStrict(final String json) throws JsonProcessingException {
        return inflate(STRICT_MAPPER.readValue(json, FlatEngineConfig.class));
    }

    private static FlatEngineConfig flatten(final EngineConfig config) {
        final World world = config.world();
        final Founder founder = config.founder();
        final Metabolism metabolism = config.metabolism();
        final Harvest harvest = config.harvest();
        final Motility motility = config.motility();
        final Growth growth = config.growth();
        final Sensing sensing = config.sensing();
        final Reproduction reproduction = config.reproduction();
        final Mutation mutation = config.mutation();
        return new FlatEngineConfig(
                config.seed(),
                world.worldWidth(),
                world.maxGenes(),
                config.chemicals(),
                founder.initialEnergy(),
                metabolism.basalCost(),
                metabolism.costPerPropagation(),
                harvest.capacityPerConnection(),
                harvest.halfSaturation(),
                harvest.massExponent(),
                motility.fixedCost(),
                motility.costPerMass(),
                motility.deadzone(),
                sensing.selfEnergyScale(),
                sensing.selfMassScale(),
                founder.initialMass(),
                metabolism.maintenancePerMass(),
                growth.max(),
                growth.halfSaturation(),
                growth.yield(),
                reproduction.buildCost(),
                reproduction.buildCostPerGene(),
                mutation.weightRate(),
                mutation.structRate(),
                mutation.duplicationRate(),
                mutation.deletionRate(),
                metabolism.agingCost());
    }

    private static EngineConfig inflate(final FlatEngineConfig flat) {
        final World world = new World(flat.worldWidth(), flat.maxGenes());
        EngineConfig.validateChemicals(flat.chemicals());
        final Founder founder = new Founder(flat.initialEnergy(), flat.initialMass());
        final Metabolism metabolism =
                new Metabolism(flat.basalCost(), flat.costPerPropagation(), flat.maintPerMass(), flat.agingCost());
        final Harvest harvest = new Harvest(flat.harvestCapacityPerConnection(), flat.harvestHalfSaturation(),
                flat.harvestMassExponent());
        final Motility motility = new Motility(flat.moveCostBase(), flat.moveCostPerMass(), flat.moveDeadzone());
        final Sensing sensing = new Sensing(flat.selfEnergyScale(), flat.selfMassScale());
        final Growth growth = new Growth(flat.growMax(), flat.growHalfSaturation(), flat.growYield());
        final Reproduction reproduction = new Reproduction(flat.buildCost(), flat.buildCostPerGene());
        final Mutation mutation = new Mutation(flat.mutationRateWeight(), flat.mutationRateStruct(),
                flat.indelRateDup(), flat.indelRateDel());
        return new EngineConfig(flat.seed(), world, flat.chemicals(), founder, metabolism, harvest, motility, growth,
                sensing, reproduction, mutation);
    }
}
