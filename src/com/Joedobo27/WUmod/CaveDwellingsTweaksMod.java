package com.Joedobo27.WUmod;


import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.WurmPermissions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.structures.WallEnum;
import com.wurmonline.server.zones.NoSuchZoneException;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zone;
import com.wurmonline.server.zones.Zones;
import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;


import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CaveDwellingsTweaksMod implements WurmServerMod, Configurable, Initable, ServerStartedListener {

    private static boolean allowBuildAgainstWalls = false;
    private static int maxStories = 8;
    private static boolean allowAnyBuildingType = false;
    private static boolean buildOnPlainCaveFloor = false;
    private static boolean removeAcceleratedOffDeedDecay = false;
    @SuppressWarnings("unused")
    public static byte typeExpand = 0;
    @SuppressWarnings("unused")
    public static byte typePlan = 0;
    @SuppressWarnings("unused")
    public static byte typeBehaviour = 0;

    private static ClassPool pool;

    private static final Logger logger = Logger.getLogger(CaveDwellingsTweaksMod.class.getName());

    @Override
    public void configure(Properties properties) {
        allowBuildAgainstWalls = Boolean.parseBoolean(properties.getProperty("allowBuildAgainstWalls", Boolean.toString(allowBuildAgainstWalls)));
        maxStories = Integer.parseInt(properties.getProperty("maxStories", Integer.toString(maxStories)));
        allowAnyBuildingType = Boolean.parseBoolean(properties.getProperty("allowAnyBuildingType", Boolean.toString(allowAnyBuildingType)));
        buildOnPlainCaveFloor = Boolean.parseBoolean(properties.getProperty("buildOnPlainCaveFloor", Boolean.toString(buildOnPlainCaveFloor)));
        removeAcceleratedOffDeedDecay = Boolean.parseBoolean(properties.getProperty("removeAcceleratedOffDeedDecay", Boolean.toString(removeAcceleratedOffDeedDecay)));
    }

    @Override
    public void init() {
        try {
            pool = HookManager.getInstance().getClassPool();
            allowBuildAgainstWallsBytecode();
            allowAnyBuildingTypeBytecode();
            buildOnPlainCaveFloorBytecode();
            removeAcceleratedOffDeedDecayBytecode();
        }catch(NotFoundException | CannotCompileException e){
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    @Override
    public void onServerStarted() {
        try {
            allowAnyBuildingTypeReflection();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    @SuppressWarnings("unused")
    static public byte[] getMaterialsFromToolTypeHook(final Item tool, final Creature performer) {
        switch (tool.getTemplateId()) {
            case 62:
            case 63: {
                return new byte[]{0, 3};
            }
            case 493: {
                return new byte[]{1, 4};
            }
            case 176: {
                if (!WurmPermissions.mayUseGMWand(performer)) {
                    return new byte[0];
                }
                if (tool.getAuxData() == 1) {
                    return new byte[]{1};
                }
                if (tool.getAuxData() == 2) {
                    return new byte[]{3};
                }
                if (tool.getAuxData() == 3) {
                    return new byte[]{4};
                }
                return new byte[]{0};
            }
            case 315: {
                if (performer.getPower() >= 2 && Servers.isThisATestServer()) {
                    return new byte[]{0};
                }
                return new byte[0];
            }
            default: {
                return new byte[0];
            }
        }
    }

    @SuppressWarnings("unused")
    static public byte isValidCaveDwellingBuildTile(byte type){
        byte result = (type == Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id || type == Tiles.Tile.TILE_CAVE.id) ? type : ++type;
        logger.log(Level.FINE, "isValidCaveDwellingBuildTile " + Byte.toString(result));
        return result;
    }

    @SuppressWarnings("unused")
    static public boolean isCornerFixedByStructure(int tileX, int tileY, Creature performer) {
        boolean toReturn = false;
        for (int x2 = 1; x2 >= -1; --x2) {
            for (int y2 = 1; y2 >= -1; --y2) {
                try {
                    final Zone zone = Zones.getZone(tileX + x2, tileY + y2, performer.isOnSurface());
                    final VolaTile vtile = zone.getTileOrNull(tileX + x2, tileY + y2);
                    if (vtile != null) {
                        if (vtile.getStructure() != null) {
                            if (vtile.getStructure().isTypeHouse()) {
                                performer.getCommunicator().sendNormalServerMessage("The house is in the way.");
                                toReturn = true;
                                break;
                            }
                        }
                    }
                } catch (NoSuchZoneException ignored) {}
            }
            if (toReturn)
                break;
        }
        return toReturn;
    }

    static private void allowBuildAgainstWallsBytecode() throws NotFoundException, CannotCompileException {
        if (!allowBuildAgainstWalls)
            return;
        final boolean[] allowBuildAgainstWallsDone = {false};
        CtClass ctcMethodsStructure = pool.get("com.wurmonline.server.behaviours.MethodsStructure");
        ctcMethodsStructure.instrument( new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("needSurroundingTilesFloors", methodCall.getMethodName())){
                    methodCall.replace("$_ = false;");
                    logger.log(Level.FINE, "Installed hook for needSurroundingTilesFloors() at " + methodCall.getLineNumber());
                    allowBuildAgainstWallsDone[0] = true;
                }
            }
        });

        switch (Arrays.toString(allowBuildAgainstWallsDone)) {
            case "[false]":
                logger.log(Level.INFO, "ERROR allowBuildAgainstWalls - MethodsStructure.class NOT changed");
                break;
            case "[true]":
                logger.log(Level.INFO, "allowBuildAgainstWalls changes successful.");
        }
    }

    @SuppressWarnings("unused")
    static private void maxStoriesBytecode() {
        /*
        com.wurmonline.server.behaviours.CaveTileBehaviour...flatten() and handle_MINE().

        final short cceil = (short)(Tiles.decodeData(digCorner) & 0xFF);
        if (cceil >= 254) {
                comm.sendNormalServerMessage("Lowering the floor further would make the cavern unstable.");
                return true;
            }

        com.wurmonline.server.behaviours.MethodsStructure...floorPlan()
        if (layer < 0 && (floorType == StructureConstants.FloorType.OPENING || floorType == StructureConstants.FloorType.DOOR || floorType == StructureConstants.FloorType.STAIRCASE || floorType == StructureConstants.FloorType.WIDE_STAIRCASE || floorType == StructureConstants.FloorType.LEFT_STAIRCASE || floorType == StructureConstants.FloorType.RIGHT_STAIRCASE || floorType == StructureConstants.FloorType.ANTICLOCKWISE_STAIRCASE || floorType == StructureConstants.FloorType.CLOCKWISE_STAIRCASE)) {
            short ceil = 260;
            for (int x = 0; x <= 1; ++x) {
                for (int y = 0; y <= 1; ++y) {
                    final int tile = Server.caveMesh.getTile(tilex + x, tiley + y);
                    final short ht = (short)(Tiles.decodeData(tile) & 0xFF);
                    if (ht < ceil) {
                        ceil = ht;
                    }
                }
            }
            if (floorBuildOffset + 30 > ceil) {
                performer.getCommunicator().sendNormalServerMessage("There is not enough room for a further floor. E.g. ceiling is too close.");
                return true;
            }
        }

        */
    }

    static private void allowAnyBuildingTypeBytecode() throws NotFoundException, CannotCompileException {
        if (!allowAnyBuildingType)
            return;

        final boolean[] allowAnyBuildingTypeDone = {false};
        CtMethod ctmGetWallsByTool = pool.get("com.wurmonline.server.structures.WallEnum").getMethod("getWallsByTool",
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;ZZ)Ljava/util/List;");
        ctmGetWallsByTool.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getMaterialsFromToolType", methodCall.getMethodName())) {
                    methodCall.replace("$_ = com.Joedobo27.WUmod.CaveDwellingsTweaksMod.getMaterialsFromToolTypeHook($1, $2);");
                    logger.log(Level.FINE, "Installed Hook in getWallsByTool at " + methodCall.getLineNumber());
                    allowAnyBuildingTypeDone[0] = true;
                }
            }
        });

        switch (Arrays.toString(allowAnyBuildingTypeDone)) {
            case "[false]":
                logger.log(Level.INFO, "ERROR allowAnyBuildingType - getWallsByTool() NOT changed");
                break;
            case "[true]":
                logger.log(Level.INFO, "allowAnyBuildingType changes successful.");
        }
    }

    static private void buildOnPlainCaveFloorBytecode() throws NotFoundException, CannotCompileException {
        if (!buildOnPlainCaveFloor)
            return;
        final boolean[] buildOnPlainCaveFloorDone = {false, false, false};
        CtMethod ctmCanPlanStructureAt = pool.get("com.wurmonline.server.behaviours.MethodsStructure").getDeclaredMethod("canPlanStructureAt");
        ctmCanPlanStructureAt.instrument(new ExprEditor() {

            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("decodeType", methodCall.getMethodName())){
                    methodCall.replace("{ $_ = $proceed($$); com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typePlan = $_;" +
                            "com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typePlan = " +
                            "com.Joedobo27.WUmod.CaveDwellingsTweaksMod.isValidCaveDwellingBuildTile(com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typePlan);}");
                    logger.log(Level.FINE, " installed hook on decodeType() in canPlanStructureAt() to fetch the tile type at " + methodCall.getLineNumber());
                }
            }

            @Override
            public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                if (Objects.equals("id", fieldAccess.getFieldName()) && Objects.equals("com.wurmonline.mesh.Tiles$Tile", fieldAccess.getClassName())) {
                    fieldAccess.replace("$_ = com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typePlan;");
                    logger.log(Level.FINE, "buildOnPlainCaveFloorBytecode enabled in canPlanStructureAt() at " + fieldAccess.getLineNumber());
                    buildOnPlainCaveFloorDone[0] = true;
                }
            }
        });

        CtMethod ctmExpandHouseTile = pool.get("com.wurmonline.server.behaviours.MethodsStructure").getDeclaredMethod("expandHouseTile");
        ctmExpandHouseTile.instrument(new ExprEditor() {

            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("decodeType", methodCall.getMethodName()) && methodCall.getLineNumber() == 428){
                    methodCall.replace("{ $_ = $proceed($$); com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typeExpand = $_;" +
                            "com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typeExpand = " +
                            "com.Joedobo27.WUmod.CaveDwellingsTweaksMod.isValidCaveDwellingBuildTile(com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typeExpand);}");
                    logger.log(Level.FINE, "installed hook on decodeType() in expandHouseTile() to fetch the tile type at " + methodCall.getLineNumber());
                }
            }

            @Override
            public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                if (Objects.equals("id", fieldAccess.getFieldName()) && Objects.equals("com.wurmonline.mesh.Tiles$Tile", fieldAccess.getClassName())) {
                    fieldAccess.replace("$_ = com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typeExpand;");
                    logger.log(Level.FINE, "buildOnPlainCaveFloorBytecode enabled in canPlanStructureAt() at " + fieldAccess.getLineNumber());
                    buildOnPlainCaveFloorDone[1] = true;
                }
            }
        });

        CtMethod ctmGetBehavioursFor_CaveTileBehaviour = pool.get("com.wurmonline.server.behaviours.CaveTileBehaviour").getMethod("getBehavioursFor",
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIZII)Ljava/util/List;");
        ctmGetBehavioursFor_CaveTileBehaviour.instrument(new ExprEditor() {

            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("decodeType", methodCall.getMethodName()) && methodCall.getLineNumber() == 114){
                    methodCall.replace("{ $_ = $proceed($$); com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typeBehaviour = $_;" +
                            "com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typeBehaviour = " +
                            "com.Joedobo27.WUmod.CaveDwellingsTweaksMod.isValidCaveDwellingBuildTile(com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typeBehaviour);}");
                    logger.log(Level.FINE, "installed hook on decodeType() in getBehavioursFor() to fetch the tile type at " + methodCall.getLineNumber());
                }
            }

            @Override
            public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                if (Objects.equals("id", fieldAccess.getFieldName()) && Objects.equals("com.wurmonline.mesh.Tiles$Tile", fieldAccess.getClassName()) &&
                        fieldAccess.getLineNumber() == 116) {
                    fieldAccess.replace("$_ = com.Joedobo27.WUmod.CaveDwellingsTweaksMod.typeBehaviour;");
                    logger.log(Level.FINE, "buildOnPlainCaveFloorBytecode enabled in canPlanStructureAt() at " + fieldAccess.getLineNumber());
                    buildOnPlainCaveFloorDone[2] = true;
                }
            }
        });

        // Add method to test if a corner's alteration should be blocked by a structure.
        CtClass ctcCaveTileBehaviour = pool.get("com.wurmonline.server.behaviours.CaveTileBehaviour");
        CtMethod ctmIsCornerFixedByStructure = new CtMethod(CtClass.booleanType, "isCornerFixedByStructure", new CtClass[]
                {CtClass.intType, CtClass.intType, pool.get("com.wurmonline.server.creatures.Creature")}, ctcCaveTileBehaviour);
        ctmIsCornerFixedByStructure.setModifiers(Modifier.STATIC);
        ctcCaveTileBehaviour.addMethod(ctmIsCornerFixedByStructure);
        CtMethod ctmIsCornerFixedByStructure1 = pool.get("com.Joedobo27.WUmod.CaveDwellingsTweaksMod").getMethod("isCornerFixedByStructure",
                "(IILcom/wurmonline/server/creatures/Creature;)Z");
        ctmIsCornerFixedByStructure.setBody(ctmIsCornerFixedByStructure1, null);
        ctcCaveTileBehaviour.setModifiers(ctcCaveTileBehaviour.getModifiers() & ~Modifier.ABSTRACT);

        // Insert code to call isCornerFixedByStructure and react to the return value appropriately.
        CtMethod ctmHandle_MINE = ctcCaveTileBehaviour.getDeclaredMethod("handle_MINE");
        String string2 = "if (com.wurmonline.server.behaviours.CaveTileBehaviour.isCornerFixedByStructure($4, $5, $2)) {";
        string2 += "$2.getCommunicator().sendNormalServerMessage(\"The structure is in the way.\");return true;}";

        ctmHandle_MINE.insertAt(875, "{ " + string2 + " }");

        switch (Arrays.toString(buildOnPlainCaveFloorDone)){
            case "[true, true, true]":
                logger.log(Level.INFO, "buildOnPlainCaveFloor changes successful");
                break;
            case "[false, false, false]":
                logger.log(Level.INFO, "ERROR all edits failed in buildOnPlainCaveFloorBytecode");
                break;
            default: logger.log(Level.INFO, "ERROR at least one edit failed in buildOnPlainCaveFloorBytecode");
        }
    }

    static private void removeAcceleratedOffDeedDecayBytecode() throws NotFoundException, CannotCompileException {
        if (!removeAcceleratedOffDeedDecay)
            return;
        final boolean[] removeAcceleratedOffDeedDecayDone = {false, false};
        CtMethod ctmPoll_Floor = pool.get("com.wurmonline.server.structures.Floor").getMethod("poll",
                "(JLcom/wurmonline/server/zones/VolaTile;Lcom/wurmonline/server/structures/Structure;)Z");
        ctmPoll_Floor.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("isOnSurface", methodCall.getMethodName())){
                    methodCall.replace("$_ = true;");
                    logger.log(Level.FINE, "Within Floor.class poll() altered isOnSurface to always be true at " + methodCall.getLineNumber());
                    removeAcceleratedOffDeedDecayDone[0] = true;
                }
            }
        });

        CtMethod ctmPoll_Wall = pool.get("com.wurmonline.server.structures.Wall").getMethod("poll",
                "(JLcom/wurmonline/server/zones/VolaTile;Lcom/wurmonline/server/structures/Structure;)V");
        ctmPoll_Wall.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws  CannotCompileException {
                if (Objects.equals("isOnSurface", methodCall.getMethodName()) && methodCall.getLineNumber() == 1254){
                    methodCall.replace("$_ = true;");
                    logger.log(Level.FINE, "Within Wall.class poll() altered isOnSurface to always be true at " + methodCall.getLineNumber());
                    removeAcceleratedOffDeedDecayDone[1] = true;
                }
            }
        });

        switch (Arrays.toString(removeAcceleratedOffDeedDecayDone)) {
            case "[true, true]":
                logger.log(Level.INFO, "removeAcceleratedOffDeedDecay changes successful.");
                break;
            case "[false, false]":
                logger.log(Level.INFO, "ERROR all of removeAcceleratedOffDeedDecay failed.");
                break;
            default: logger.log(Level.INFO, "ERROR at least one part failed in removeAcceleratedOffDeedDecay.");
        }
    }

    static private void allowAnyBuildingTypeReflection() throws NoSuchFieldException, IllegalAccessException  {
        if (!allowAnyBuildingType)
            return;
        WallEnum[] oldValues = ReflectionUtil.getPrivateField(WallEnum.class, ReflectionUtil.getField(WallEnum.class, "$VALUES"));
        for (Object a : oldValues){
            ReflectionUtil.setPrivateField(a, ReflectionUtil.getField(WallEnum.class, "surfaceOnly"), Boolean.FALSE);
            String name = ReflectionUtil.getPrivateField(a, ReflectionUtil.getField(WallEnum.class, "name"));
            logger.log(Level.FINE, name + " in WallEnum set surfaceOnly to false.");
        }
        logger.log(Level.INFO, "allowAnyBuildingType changes successful for WallEnum.");
    }
}
