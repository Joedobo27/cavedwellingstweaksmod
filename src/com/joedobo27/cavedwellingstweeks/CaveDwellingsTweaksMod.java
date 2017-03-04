package com.joedobo27.cavedwellingstweeks;


import com.wurmonline.server.creatures.Creature;
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
    private static ClassPool pool;
    private static final Logger logger;

    static {
        logger = Logger.getLogger(CaveDwellingsTweaksMod.class.getName());
        pool = HookManager.getInstance().getClassPool();
    }

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
            JAssistClassData.voidClazz();

            allowAnyBuildingTypeReflection();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    @SuppressWarnings("unused")
    static public boolean isTileCornerFixedByStructure(Creature performer,int targetTileX, int targetTileY, boolean useTargetedTile) {
        boolean toReturn = false;
        final int performerTileX = (int)performer.getStatus().getPositionX() + 2 >> 2;
        final int performerTileY = (int)performer.getStatus().getPositionY() + 2 >> 2;
        int tileX;
        int tileY;
        int rangeBeginX;
        int rangeBeginY;
        int rangeEndX;
        int rangeEndY;
        if (useTargetedTile){
            // Checking the target tile involves checking all 4 corners of that tile and it's 8 surrounding tiles.
            // Ideally the action would only get stopped if a corner needing to be modified has a structure on it. Sadly, the
            // code to make that decision is set up in procedural style instead of an OOP. Further, the procedure does
            // more then calculate where rock needs to be moved. I can't use only part of large complicated procedural based
            // method/function.
            tileX = targetTileX;
            tileY = targetTileY;
            rangeBeginX = -1;
            rangeBeginY = -1;
            rangeEndX = 1;
            rangeEndY = 1;
        } else {
            // checking the performer's corner involves checking the 4 tiles sharing that corner.
            tileX = performerTileX;
            tileY = performerTileY;
            rangeBeginX = -1;
            rangeBeginY = -1;
            rangeEndX = 0;
            rangeEndY = 0;
        }
        try {
            for (int x = rangeBeginX; x <= rangeEndX; ++x) {
                for (int y = rangeBeginY; y <= rangeEndY; ++y) {
                    final Zone zone = Zones.getZone(tileX + x, tileY + y, performer.isOnSurface());
                    final VolaTile volaTile = zone.getTileOrNull(tileX + x, tileY + y);
                    if (volaTile != null && volaTile.getStructure() != null) {
                        if (volaTile.getStructure().isTypeHouse()) {
                            performer.getCommunicator().sendNormalServerMessage("You cannot do mining related tasks next to a structure.", (byte) 3);
                        }
                        toReturn = true;
                    }
                }
            }
        } catch (NoSuchZoneException ignored) {}
        return toReturn;
    }

    /**
     * For all of MethodsStructure.class edit the calls to needSurroundingTilesFloors() so it always returns false.
     * This will allow building right next to to cave walls.
     *
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    static private void allowBuildAgainstWallsBytecode() throws NotFoundException, CannotCompileException {
        if (!allowBuildAgainstWalls)
            return;
        final int[] successes = new int[]{0};

        JAssistClassData methodsStructure = JAssistClassData.getClazz("MethodsStructure");
        if (methodsStructure == null)
            methodsStructure = new JAssistClassData("com.wurmonline.server.behaviours.MethodsStructure", pool);
        methodsStructure.getCtClass().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("needSurroundingTilesFloors", methodCall.getMethodName())){
                    logger.log(Level.FINE, "MethodsStructure class,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = false;");
                    successes[0] = 1;
                }
            }
        });
        evaluateChangesArray(successes, "allowBuildAgainstWalls");
    }

    @SuppressWarnings("unused")
    static private void maxStoriesBytecode() {
        /*
        expanding beyond 8 stories probably isn't possible without redoing how the game works with MeshIO caveMesh.

        regarding: "Tiles.decodeData(digCorner) & 0xFF", digCorner is an integer which is split up in byte groupings for
        different value.
        0xff000000 = accessed with Tiles.decodeData(encodedTile), data or in this case the ceiling offset from the floor. max at 256
        0x00ff0000 = accessed with Tiles.decodeType(), the tile type.
        0x0000ffff = accessed with Tiles.decodeHeight(encodedTile), the tile corner's elevation.

        com.wurmonline.server.behaviours.CaveTileBehaviour...flatten() and handle_MINE().

        final short cceil = (short)(Tiles.decodeData(digCorner) & 0xFF);
        if (cceil >= 254) {
                comm.sendNormalServerMessage("Lowering the floor further would make the cavern unstable.");
                return true;
            }

        com.wurmonline.server.behaviours.methodsStructure...floorPlan()
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

    /**
     * Within WallEnum.getMaterialsFromToolType() edit the calls to isOnSurface() so it always true. This will make it so
     * the restrictive cave options in WallEnum won't be used. To put it another way, treat cave dwellings exactly like
     * surface dwellings.
     *
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    static private void allowAnyBuildingTypeBytecode() throws NotFoundException, CannotCompileException {
        if (!allowAnyBuildingType)
            return;
        final int[] successes = new int[]{0};
        JAssistClassData wallEnum = JAssistClassData.getClazz("WallEnum");
        if (wallEnum == null)
            wallEnum = new JAssistClassData("com.wurmonline.server.structures.WallEnum", pool);
        JAssistMethodData getMaterialsFromToolType = new JAssistMethodData(wallEnum,
                "(Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/creatures/Creature;)[B", "getMaterialsFromToolType");
        getMaterialsFromToolType.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("isOnSurface", methodCall.getMethodName())){
                    logger.log(Level.FINE, "getMaterialsFromToolType method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = true;");
                    successes[0] = 1;
                }
            }
        });
        evaluateChangesArray(successes, "allowAnyBuildingType");
    }

    /**
     * In MethodsStructure.canPlanStructureAt() remove the logic check that blocks planning a structure when the floor isn't reinforced.
     * was-
     *      if (type != Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id) {...}
     * becomes-
     *      if (type != type) {...}
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    static private int[] canPlanStructureAtBytecodeAlter() throws NotFoundException, CannotCompileException {
        final int[] successes = new int[]{0};
        JAssistClassData methodsStructure = JAssistClassData.getClazz("MethodsStructure");
        if (methodsStructure == null)
            methodsStructure = new JAssistClassData("com.wurmonline.server.behaviours.MethodsStructure", pool);
        JAssistMethodData  canPlanStructureAt =  new JAssistMethodData(methodsStructure,
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;III)Z", "canPlanStructureAt");
        canPlanStructureAt.getCtMethod().instrument(new ExprEditor(){
            @Override
            public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                if (Objects.equals("id", fieldAccess.getFieldName()) && Objects.equals("com.wurmonline.mesh.Tiles$Tile", fieldAccess.getClassName())) {
                    logger.log(Level.FINE, "canPlanStructureAt field,  edit call to " +
                            fieldAccess.getFieldName() + " at index " + fieldAccess.getLineNumber());
                    fieldAccess.replace("$_ = type;");
                    successes[0] = 1;
                }
            }
        });
        return successes;
    }

    /**
     * in MethodsStructure.expandHouseTile() remove the logic check that blocks planning a structure when the floor isn't reinforced.
     * was-
     *      if (Tiles.decodeType(tile) != Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id) {...}
     * becomes-
     *      if (Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id != Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id) {...}
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    static private int[] expandHouseTileBytecodeAlter() throws NotFoundException, CannotCompileException {
        final int[] successes = new int[]{0};

        JAssistClassData methodsStructure = JAssistClassData.getClazz("MethodsStructure");
        if (methodsStructure == null)
            methodsStructure = new JAssistClassData("com.wurmonline.server.behaviours.MethodsStructure", pool);
        JAssistMethodData expandHouseTile = new JAssistMethodData(methodsStructure,
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIIF)Z", "expandHouseTile");
        expandHouseTile.getCtMethod().instrument(new ExprEditor(){
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("decodeType", methodCall.getMethodName()) && methodCall.getLineNumber() == 429){
                    logger.log(Level.FINE, "expandHouseTile method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = com.wurmonline.mesh.Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id;");
                    successes[0] = 1;
                }
            }
        });
        return successes;
    }

    /**
     * In CaveTileBehaviour.getBehavioursFor(LCreature;LItem;IIZII) remove the logic that blocks adding menu actions if
     * the cave floor isn't reinforced.
     * was-
     *      if (Features.Feature.CAVE_DWELLINGS.isEnabled() && type == Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id && dir == 0) {...}
     * becomes-
     *      if (Features.Feature.CAVE_DWELLINGS.isEnabled() && type == type && dir == 0) {...}
     *
     * @return int[] object. Where changes successful, yes 1, no 0.
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    static private int[] getBehavioursForCaveTileBytecodeAlter() throws NotFoundException, CannotCompileException {
        final int[] successes = new int[]{0};

        JAssistClassData caveTileBehaviour = JAssistClassData.getClazz("CaveTileBehaviour");
        if (caveTileBehaviour == null)
            caveTileBehaviour = new JAssistClassData("com.wurmonline.server.behaviours.CaveTileBehaviour", pool);
        JAssistMethodData getBehavioursFor = new JAssistMethodData(caveTileBehaviour,
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIZII)Ljava/util/List;", "getBehavioursFor");
        getBehavioursFor.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                if (Objects.equals("id", fieldAccess.getFieldName()) && Objects.equals("com.wurmonline.mesh.Tiles$Tile", fieldAccess.getClassName()) &&
                        fieldAccess.getLineNumber() == 116) {
                    logger.log(Level.FINE, "getBehavioursFor method,  edit call to field " +
                            fieldAccess.getFieldName() + " at index " + fieldAccess.getLineNumber());
                    fieldAccess.replace("$_ = type;");
                    successes[0] = 1;
                }
            }
        });
        return successes;
    }

    /**
     * Find the line position of the Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id field accessor and insert my calls to
     * isTileCornerFixedByStructure() at that line. There needs to be code to block using level when it would alter the tile under
     * a structure. This is not in WU as default relies on the tile being reinforced.
     *
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    static private void handle_LEVELBytecodeAlter() throws NotFoundException, CannotCompileException {
        final int[] insertLineNumber = new int[]{0};
        JAssistClassData caveTileBehaviour = JAssistClassData.getClazz("CaveTileBehaviour");
        if (caveTileBehaviour == null)
            caveTileBehaviour = new JAssistClassData("com.wurmonline.server.behaviours.CaveTileBehaviour", pool);
        JAssistMethodData handle_LEVEL = new JAssistMethodData(caveTileBehaviour,
                "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIIFI)Z",
                "handle_LEVEL");
        handle_LEVEL.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(FieldAccess fieldAccess){
                if (Objects.equals("id", fieldAccess.getFieldName())) {
                    insertLineNumber[0] = fieldAccess.getLineNumber();
                }
            }
        });
        if (insertLineNumber[0] == 0)
            throw new NullPointerException("Didn't find id field in handle_LEVEL");

        String source = "{ boolean isStructureOnCorner = dir == 0 && ";
        source += "com.joedobo27.cavedwellingstweeks.CaveDwellingsTweaksMod.isTileCornerFixedByStructure(performer, tilex, tiley, true);";
        source += "if (isStructureOnCorner){return true;} }";
        handle_LEVEL.getCtMethod().insertAt(insertLineNumber[0], source);
    }

    static private void handle_FLATTENBytecodeAlter() throws NotFoundException, CannotCompileException {
        JAssistClassData caveTileBehaviour = JAssistClassData.getClazz("CaveTileBehaviour");
        if (caveTileBehaviour == null)
            caveTileBehaviour = new JAssistClassData("com.wurmonline.server.behaviours.CaveTileBehaviour", pool);

        JAssistMethodData handle_FLATTEN = new JAssistMethodData(caveTileBehaviour,
                "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIIFI)Z",
                "handle_FLATTEN");
        String source = "{ boolean isStructureOnCorner = dir == 0 && ";
        source += "com.joedobo27.cavedwellingstweeks.CaveDwellingsTweaksMod.isTileCornerFixedByStructure(performer, tilex, tiley, true);";
        source += "if (isStructureOnCorner){return true;} }";
        handle_FLATTEN.getCtMethod().insertAt(721, source);
        //insert right before:  if (Tiles.decodeType(tile) == Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id && dir == 0) {

    }

    static private void handle_MINEBytecodeAlter() throws NotFoundException, CannotCompileException {
        JAssistClassData caveTileBehaviour = JAssistClassData.getClazz("CaveTileBehaviour");
        if (caveTileBehaviour == null)
            caveTileBehaviour = new JAssistClassData("com.wurmonline.server.behaviours.CaveTileBehaviour", pool);
        JAssistMethodData handle_MINE = new JAssistMethodData(caveTileBehaviour,
                "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IISFI)Z",
                "handle_MINE");
        String source = "{ boolean isStructureOnCorner = dir == 0 && com.joedobo27.cavedwellingstweeks.CaveDwellingsTweaksMod.isTileCornerFixedByStructure(performer, tilex, tiley, false);";
        source += "if (isStructureOnCorner){return true;} }";
        handle_MINE.getCtMethod().insertAt(965, source);
        //insert right before: if (dir == 0 && tileType == Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id && performer.getPower() < 2) {
    }

    static private void raiseRockLevelBytecodeAlter() throws NotFoundException, CannotCompileException {
        JAssistClassData caveTileBehaviour = JAssistClassData.getClazz("CaveTileBehaviour");
        if (caveTileBehaviour == null)
            caveTileBehaviour = new JAssistClassData("com.wurmonline.server.behaviours.CaveTileBehaviour", pool);
        JAssistMethodData raiseRockLevel = new JAssistMethodData(caveTileBehaviour,
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIFLcom/wurmonline/server/behaviours/Action;)Z",
                "raiseRockLevel");
        source = "{ boolean isStructureOnCorner = com.joedobo27.cavedwellingstweeks.CaveDwellingsTweaksMod.isTileCornerFixedByStructure(performer, tilex, tiley, false);";
        source += "if (isStructureOnCorner){return true;} }";
        raiseRockLevel.getCtMethod().insertAt(1290, source);
        // insert right before: if (CaveTile.decodeCeilingHeight(tile) <= 20) {
    }

    static private void buildOnPlainCaveFloorBytecode() throws NotFoundException, CannotCompileException {
        if (!buildOnPlainCaveFloor)
            return;
        final int[] successes = new int[]{0,0,0};
        int[] result;

        result = canPlanStructureAtBytecodeAlter();
        System.arraycopy( result, 0, successes, 0, result.length );

        result = expandHouseTileBytecodeAlter();
        System.arraycopy( result, 0, successes, 1, result.length );

        result = getBehavioursForCaveTileBytecodeAlter();
        System.arraycopy( result, 0, successes, 2, result.length );

        evaluateChangesArray(successes, "buildOnPlainCaveFloor");

        // There is no code in WU that stops mining actions (mine, flatten, level) or using concrete on corners that have structures on them.
        // I'll be inserting lines that call my structure detection method "isTileCornerFixedByStructure".
        handle_LEVELBytecodeAlter();
        handle_FLATTENBytecodeAlter();
        handle_MINEBytecodeAlter();
        raiseRockLevelBytecodeAlter();
    }

    static private void removeAcceleratedOffDeedDecayBytecode() throws NotFoundException, CannotCompileException {
        if (!removeAcceleratedOffDeedDecay)
            return;
        final int[] successes = {0, 0};
        JAssistClassData floor = new JAssistClassData("com.wurmonline.server.structures.Floor", pool);
        JAssistMethodData pollFloor = new JAssistMethodData(floor, "(JLcom/wurmonline/server/zones/VolaTile;Lcom/wurmonline/server/structures/Structure;)Z",
                "poll");
        pollFloor.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("isOnSurface", methodCall.getMethodName())){
                    logger.log(Level.FINE, pollFloor.getCtMethod().getName() + " method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = true;");
                    successes[0] = 1;
                }
            }
        });

        JAssistClassData wall = new JAssistClassData("com.wurmonline.server.structures.Wall", pool);
        JAssistMethodData pollWall = new JAssistMethodData(wall, "(JLcom/wurmonline/server/zones/VolaTile;Lcom/wurmonline/server/structures/Structure;)V",
                "poll");
        pollWall.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws  CannotCompileException {
                if (Objects.equals("isOnSurface", methodCall.getMethodName()) && methodCall.getLineNumber() == 1254){
                    logger.log(Level.FINE, pollFloor.getCtMethod().getName() + " method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = true;");
                    successes[1] = 1;
                }
            }
        });
        evaluateChangesArray(successes, "removeAcceleratedOffDeedDecay");
    }

    static private void allowAnyBuildingTypeReflection() throws NoSuchFieldException, IllegalAccessException  {
        if (!allowAnyBuildingType)
            return;
        WallEnum[] oldValues = ReflectionUtil.getPrivateField(WallEnum.class, ReflectionUtil.getField(WallEnum.class, "$VALUES"));
        for (Object a : oldValues){
            ReflectionUtil.setPrivateField(a, ReflectionUtil.getField(WallEnum.class, "surfaceOnly"), Boolean.FALSE);
            String name = ReflectionUtil.getPrivateField(a, ReflectionUtil.getField(WallEnum.class, "name"));
        }
        logger.log(Level.INFO, "allowAnyBuildingType changes successful for WallEnum.");
    }

    private static void evaluateChangesArray(int[] ints, String option) {
        boolean changesSuccessful = !Arrays.stream(ints).anyMatch(value -> value == 0);
        if (changesSuccessful) {
            logger.log(Level.INFO, option + " option changes SUCCESSFUL");
        } else {
            logger.log(Level.INFO, option + " option changes FAILURE");
            logger.log(Level.FINE, Arrays.toString(ints));
        }
    }
}
