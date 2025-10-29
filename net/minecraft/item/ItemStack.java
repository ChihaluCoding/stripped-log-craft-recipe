package net.minecraft.item;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.item.v1.FabricItemStack;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.Spawner;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentHolder;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.BlockPredicatesComponent;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.DamageResistantComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.RepairableComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.component.type.UseCooldownComponent;
import net.minecraft.component.type.UseRemainderComponent;
import net.minecraft.component.type.WeaponComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.consume.UseAction;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Unit;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.dynamic.NullOps;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Represents a stack of items. This is a data container that holds the item count
 * and the stack's components. Logics for items (such as the action for using it) are delegated
 * to the stack's logic container, {@link Item}. Instances can be created using one of
 * the constructors and are usually stored in an {@link net.minecraft.inventory.Inventory}.
 * 
 * <p>Item stacks should never be compared using {@code ==} operator or {@code equals}
 * method. This also means they cannot be used as a map key. To check if an item stack
 * is of a certain item, use {@link #isOf(Item)}. To compare two item stacks, use {@link
 * #areItemsEqual} to check the item only, or {@link #areEqual} to also check the item
 * count and the components. Use {@link #isEmpty} to check if an item stack is empty instead
 * of doing {@code stack == ItemStack.EMPTY}.
 * 
 * <p>When storing an item stack in an inventory or other places, make sure that an instance
 * is never stored in multiple places. When two inventories hold the same instance, it
 * will duplicate the item stack (and become two instances) when one is saved and reloaded.
 * 
 * <h2 id="components">Components</h2>
 * <p>Components can be used to store data specific to the item stack.
 * Use {@link ComponentHolder#get} or {@link ComponentHolder#getOrDefault} to
 * get the component values. Use {@link #set} or {@link #remove} to set the components.
 * 
 * <h2 id="nbt-serialization">NBT serialization</h2>
 * <p>An Item Stack can be serialized with {@link #toNbt(RegistryWrapper.WrapperLookup)}, and deserialized with {@link #fromNbt(RegistryWrapper.WrapperLookup, NbtCompound)}.
 * 
 * <div class="fabric">
 * <table border=1>
 * <caption>Serialized NBT Structure</caption>
 * <tr>
 *   <th>Key</th><th>Type</th><th>Purpose</th>
 * </tr>
 * <tr>
 *   <td>{@code id}</td><td>{@link net.minecraft.nbt.NbtString}</td><td>The identifier of the item.</td>
 * </tr>
 * <tr>
 *   <td>{@code count}</td><td>{@link net.minecraft.nbt.NbtInt}</td><td>The count of items in the stack.</td>
 * </tr>
 * <tr>
 *   <td>{@code components}</td><td>{@link ComponentChanges}</td><td>The item stack's components.</td>
 * </tr>
 * </table>
 * </div>
 */
public final class ItemStack implements ComponentHolder, FabricItemStack {
	private static final List<Text> OPERATOR_WARNINGS = List.of(
		Text.translatable("item.op_warning.line1").formatted(Formatting.RED, Formatting.BOLD),
		Text.translatable("item.op_warning.line2").formatted(Formatting.RED),
		Text.translatable("item.op_warning.line3").formatted(Formatting.RED)
	);
	private static final Text UNBREAKABLE_TEXT = Text.translatable("item.unbreakable").formatted(Formatting.BLUE);
	public static final MapCodec<ItemStack> MAP_CODEC = MapCodec.recursive(
		"ItemStack",
		codec -> RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Item.ENTRY_CODEC.fieldOf("id").forGetter(ItemStack::getRegistryEntry),
					Codecs.rangedInt(1, 99).fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
					ComponentChanges.CODEC.optionalFieldOf("components", ComponentChanges.EMPTY).forGetter(stack -> stack.components.getChanges())
				)
				.apply(instance, ItemStack::new)
		)
	);
	public static final Codec<ItemStack> CODEC = Codec.lazyInitialized(MAP_CODEC::codec);
	public static final Codec<ItemStack> UNCOUNTED_CODEC = Codec.lazyInitialized(
		() -> RecordCodecBuilder.create(
			instance -> instance.group(
					Item.ENTRY_CODEC.fieldOf("id").forGetter(ItemStack::getRegistryEntry),
					ComponentChanges.CODEC.optionalFieldOf("components", ComponentChanges.EMPTY).forGetter(stack -> stack.components.getChanges())
				)
				.apply(instance, (item, components) -> new ItemStack(item, 1, components))
		)
	);
	public static final Codec<ItemStack> VALIDATED_CODEC = CODEC.validate(ItemStack::validate);
	public static final Codec<ItemStack> VALIDATED_UNCOUNTED_CODEC = UNCOUNTED_CODEC.validate(ItemStack::validate);
	public static final Codec<ItemStack> OPTIONAL_CODEC = Codecs.optional(CODEC)
		.xmap(optional -> (ItemStack)optional.orElse(ItemStack.EMPTY), stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));
	public static final Codec<ItemStack> REGISTRY_ENTRY_CODEC = Item.ENTRY_CODEC.xmap(ItemStack::new, ItemStack::getRegistryEntry);
	public static final PacketCodec<RegistryByteBuf, ItemStack> OPTIONAL_PACKET_CODEC = createOptionalPacketCodec(ComponentChanges.PACKET_CODEC);
	public static final PacketCodec<RegistryByteBuf, ItemStack> LENGTH_PREPENDED_OPTIONAL_PACKET_CODEC = createOptionalPacketCodec(
		ComponentChanges.LENGTH_PREPENDED_PACKET_CODEC
	);
	public static final PacketCodec<RegistryByteBuf, ItemStack> PACKET_CODEC = new PacketCodec<RegistryByteBuf, ItemStack>() {
		public ItemStack decode(RegistryByteBuf registryByteBuf) {
			ItemStack itemStack = ItemStack.OPTIONAL_PACKET_CODEC.decode(registryByteBuf);
			if (itemStack.isEmpty()) {
				throw new DecoderException("Empty ItemStack not allowed");
			} else {
				return itemStack;
			}
		}

		public void encode(RegistryByteBuf registryByteBuf, ItemStack itemStack) {
			if (itemStack.isEmpty()) {
				throw new EncoderException("Empty ItemStack not allowed");
			} else {
				ItemStack.OPTIONAL_PACKET_CODEC.encode(registryByteBuf, itemStack);
			}
		}
	};
	public static final PacketCodec<RegistryByteBuf, List<ItemStack>> OPTIONAL_LIST_PACKET_CODEC = OPTIONAL_PACKET_CODEC.collect(
		PacketCodecs.toCollection(DefaultedList::ofSize)
	);
	private static final Logger LOGGER = LogUtils.getLogger();
	/**
	 * The empty item stack that holds no item.
	 * 
	 * <p>This should never be mutated.
	 * 
	 * @see ItemStack#isEmpty
	 */
	public static final ItemStack EMPTY = new ItemStack((Void)null);
	private static final Text DISABLED_TEXT = Text.translatable("item.disabled").formatted(Formatting.RED);
	private int count;
	private int bobbingAnimationTime;
	@Deprecated
	@Nullable
	private final Item item;
	final MergedComponentMap components;
	@Nullable
	private Entity holder;

	public static DataResult<ItemStack> validate(ItemStack stack) {
		DataResult<Unit> dataResult = validateComponents(stack.getComponents());
		if (dataResult.isError()) {
			return dataResult.map(v -> stack);
		} else {
			return stack.getCount() > stack.getMaxCount()
				? DataResult.error(() -> "Item stack with stack size of " + stack.getCount() + " was larger than maximum: " + stack.getMaxCount())
				: DataResult.success(stack);
		}
	}

	private static PacketCodec<RegistryByteBuf, ItemStack> createOptionalPacketCodec(PacketCodec<RegistryByteBuf, ComponentChanges> componentsPacketCodec) {
		return new PacketCodec<RegistryByteBuf, ItemStack>() {
			public ItemStack decode(RegistryByteBuf registryByteBuf) {
				int i = registryByteBuf.readVarInt();
				if (i <= 0) {
					return ItemStack.EMPTY;
				} else {
					RegistryEntry<Item> registryEntry = Item.ENTRY_PACKET_CODEC.decode(registryByteBuf);
					ComponentChanges componentChanges = componentsPacketCodec.decode(registryByteBuf);
					return new ItemStack(registryEntry, i, componentChanges);
				}
			}

			public void encode(RegistryByteBuf registryByteBuf, ItemStack itemStack) {
				if (itemStack.isEmpty()) {
					registryByteBuf.writeVarInt(0);
				} else {
					registryByteBuf.writeVarInt(itemStack.getCount());
					Item.ENTRY_PACKET_CODEC.encode(registryByteBuf, itemStack.getRegistryEntry());
					componentsPacketCodec.encode(registryByteBuf, itemStack.components.getChanges());
				}
			}
		};
	}

	/**
	 * {@return a packet codec that ensures the validity of the decoded stack by
	 * checking if it can be re-encoded}
	 * 
	 * <p>This should be used when serializing {@link ItemStack} in C2S packets.
	 * Encoding is unaffected.
	 */
	public static PacketCodec<RegistryByteBuf, ItemStack> createExtraValidatingPacketCodec(PacketCodec<RegistryByteBuf, ItemStack> basePacketCodec) {
		return new PacketCodec<RegistryByteBuf, ItemStack>() {
			public ItemStack decode(RegistryByteBuf registryByteBuf) {
				ItemStack itemStack = basePacketCodec.decode(registryByteBuf);
				if (!itemStack.isEmpty()) {
					RegistryOps<Unit> registryOps = registryByteBuf.getRegistryManager().getOps(NullOps.INSTANCE);
					ItemStack.CODEC.encodeStart(registryOps, itemStack).getOrThrow(DecoderException::new);
				}

				return itemStack;
			}

			public void encode(RegistryByteBuf registryByteBuf, ItemStack itemStack) {
				basePacketCodec.encode(registryByteBuf, itemStack);
			}
		};
	}

	public Optional<TooltipData> getTooltipData() {
		return this.getItem().getTooltipData(this);
	}

	@Override
	public ComponentMap getComponents() {
		return (ComponentMap)(!this.isEmpty() ? this.components : ComponentMap.EMPTY);
	}

	public ComponentMap getDefaultComponents() {
		return !this.isEmpty() ? this.getItem().getComponents() : ComponentMap.EMPTY;
	}

	public ComponentChanges getComponentChanges() {
		return !this.isEmpty() ? this.components.getChanges() : ComponentChanges.EMPTY;
	}

	public ComponentMap getImmutableComponents() {
		return !this.isEmpty() ? this.components.immutableCopy() : ComponentMap.EMPTY;
	}

	public boolean hasChangedComponent(ComponentType<?> type) {
		return !this.isEmpty() && this.components.hasChanged(type);
	}

	public ItemStack(ItemConvertible item) {
		this(item, 1);
	}

	public ItemStack(RegistryEntry<Item> entry) {
		this(entry.value(), 1);
	}

	public ItemStack(RegistryEntry<Item> item, int count, ComponentChanges changes) {
		this(item.value(), count, MergedComponentMap.create(item.value().getComponents(), changes));
	}

	public ItemStack(RegistryEntry<Item> itemEntry, int count) {
		this(itemEntry.value(), count);
	}

	public ItemStack(ItemConvertible item, int count) {
		this(item, count, new MergedComponentMap(item.asItem().getComponents()));
	}

	private ItemStack(ItemConvertible item, int count, MergedComponentMap components) {
		this.item = item.asItem();
		this.count = count;
		this.components = components;
	}

	private ItemStack(@Nullable Void v) {
		this.item = null;
		this.components = new MergedComponentMap(ComponentMap.EMPTY);
	}

	public static DataResult<Unit> validateComponents(ComponentMap components) {
		if (components.contains(DataComponentTypes.MAX_DAMAGE) && components.getOrDefault(DataComponentTypes.MAX_STACK_SIZE, 1) > 1) {
			return DataResult.error(() -> "Item cannot be both damageable and stackable");
		} else {
			ContainerComponent containerComponent = components.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT);

			for (ItemStack itemStack : containerComponent.iterateNonEmpty()) {
				int i = itemStack.getCount();
				int j = itemStack.getMaxCount();
				if (i > j) {
					return DataResult.error(() -> "Item stack with count of " + i + " was larger than maximum: " + j);
				}
			}

			return DataResult.success(Unit.INSTANCE);
		}
	}

	/**
	 * {@return whether this item stack is empty}
	 */
	public boolean isEmpty() {
		return this == EMPTY || this.item == Items.AIR || this.count <= 0;
	}

	public boolean isItemEnabled(FeatureSet enabledFeatures) {
		return this.isEmpty() || this.getItem().isEnabled(enabledFeatures);
	}

	/**
	 * {@return the copy of the stack "split" from the current stack with item count
	 * being at most {@code amount}}
	 * 
	 * <p>Splitting an item stack mutates this stack so that the sum of the stacks' item
	 * counts does not change. See the example below:
	 * 
	 * <pre>{@code
	 * ItemStack stack = new ItemStack(Items.APPLE, 64);
	 * ItemStack newStack = stack.split(10);
	 * // stack has 54 apples
	 * // newStack has 10 apples
	 * 
	 * ItemStack smallStack = new ItemStack(Items.APPLE, 4);
	 * ItemStack newSmallStack = smallStack.split(10);
	 * // smallStack is now empty
	 * // newSmallStack has 4 apples
	 * }</pre>
	 */
	public ItemStack split(int amount) {
		int i = Math.min(amount, this.getCount());
		ItemStack itemStack = this.copyWithCount(i);
		this.decrement(i);
		return itemStack;
	}

	public ItemStack copyAndEmpty() {
		if (this.isEmpty()) {
			return EMPTY;
		} else {
			ItemStack itemStack = this.copy();
			this.setCount(0);
			return itemStack;
		}
	}

	/**
	 * {@return the item of this stack}
	 * 
	 * @see #isOf(Item)
	 */
	public Item getItem() {
		return this.isEmpty() ? Items.AIR : this.item;
	}

	public RegistryEntry<Item> getRegistryEntry() {
		return this.getItem().getRegistryEntry();
	}

	/**
	 * {@return whether the item is in {@code tag}}
	 */
	public boolean isIn(TagKey<Item> tag) {
		return this.getItem().getRegistryEntry().isIn(tag);
	}

	/**
	 * {@return whether the item is {@code item}}
	 */
	public boolean isOf(Item item) {
		return this.getItem() == item;
	}

	/**
	 * {@return whether the item's registry entry passes the {@code predicate}}
	 * 
	 * @see #itemMatches(RegistryEntry)
	 * @see #isOf(Item)
	 */
	public boolean itemMatches(Predicate<RegistryEntry<Item>> predicate) {
		return predicate.test(this.getItem().getRegistryEntry());
	}

	/**
	 * {@return whether the item's registry entry matches {@code itemEntry}}
	 * 
	 * @see #itemMatches(Predicate)
	 * @see #isOf(Item)
	 */
	public boolean itemMatches(RegistryEntry<Item> itemEntry) {
		return this.getItem().getRegistryEntry() == itemEntry;
	}

	public boolean isIn(RegistryEntryList<Item> registryEntryList) {
		return registryEntryList.contains(this.getRegistryEntry());
	}

	/**
	 * {@return a stream of all tags the item is in}
	 * 
	 * @see #isIn(TagKey)
	 */
	public Stream<TagKey<Item>> streamTags() {
		return this.getItem().getRegistryEntry().streamTags();
	}

	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity playerEntity = context.getPlayer();
		BlockPos blockPos = context.getBlockPos();
		if (playerEntity != null && !playerEntity.getAbilities().allowModifyWorld && !this.canPlaceOn(new CachedBlockPosition(context.getWorld(), blockPos, false))) {
			return ActionResult.PASS;
		} else {
			Item item = this.getItem();
			ActionResult actionResult = item.useOnBlock(context);
			if (playerEntity != null && actionResult instanceof ActionResult.Success success && success.shouldIncrementStat()) {
				playerEntity.incrementStat(Stats.USED.getOrCreateStat(item));
			}

			return actionResult;
		}
	}

	public float getMiningSpeedMultiplier(BlockState state) {
		return this.getItem().getMiningSpeed(this, state);
	}

	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack itemStack = this.copy();
		boolean bl = this.getMaxUseTime(user) <= 0;
		ActionResult actionResult = this.getItem().use(world, user, hand);
		return (ActionResult)(bl && actionResult instanceof ActionResult.Success success
			? success.withNewHandStack(
				success.getNewHandStack() == null ? this.applyRemainderAndCooldown(user, itemStack) : success.getNewHandStack().applyRemainderAndCooldown(user, itemStack)
			)
			: actionResult);
	}

	public ItemStack finishUsing(World world, LivingEntity user) {
		ItemStack itemStack = this.copy();
		ItemStack itemStack2 = this.getItem().finishUsing(this, world, user);
		return itemStack2.applyRemainderAndCooldown(user, itemStack);
	}

	private ItemStack applyRemainderAndCooldown(LivingEntity user, ItemStack stack) {
		UseRemainderComponent useRemainderComponent = stack.get(DataComponentTypes.USE_REMAINDER);
		UseCooldownComponent useCooldownComponent = stack.get(DataComponentTypes.USE_COOLDOWN);
		int i = stack.getCount();
		ItemStack itemStack = this;
		if (useRemainderComponent != null) {
			itemStack = useRemainderComponent.convert(this, i, user.isInCreativeMode(), user::giveOrDropStack);
		}

		if (useCooldownComponent != null) {
			useCooldownComponent.set(stack, user);
		}

		return itemStack;
	}

	public int getMaxCount() {
		return this.getOrDefault(DataComponentTypes.MAX_STACK_SIZE, 1);
	}

	/**
	 * {@return whether the item stack can have item count above {@code 1}}
	 * 
	 * <p>Stackable items must have {@linkplain Item#getMaxCount the maximum count} that is more
	 * than {@code 1} and cannot be damaged.
	 */
	public boolean isStackable() {
		return this.getMaxCount() > 1 && (!this.isDamageable() || !this.isDamaged());
	}

	/**
	 * {@return whether the item can be damaged (lose durability)}
	 * 
	 * <p>Items with {@linkplain #getMaxDamage 0 max damage} or item stacks with {@link
	 * net.minecraft.component.DataComponentTypes#UNBREAKABLE} component cannot be damaged.
	 * 
	 * @see #getMaxDamage
	 * @see #isDamaged
	 * @see #getDamage
	 */
	public boolean isDamageable() {
		return this.contains(DataComponentTypes.MAX_DAMAGE) && !this.contains(DataComponentTypes.UNBREAKABLE) && this.contains(DataComponentTypes.DAMAGE);
	}

	/**
	 * {@return whether the item stack is {@linkplain #isDamageable damageable} and has damage}
	 * 
	 * @see #isDamageable
	 * @see #getDamage
	 */
	public boolean isDamaged() {
		return this.isDamageable() && this.getDamage() > 0;
	}

	/**
	 * {@return the damage (lost durability) of the item stack}
	 * 
	 * <p>Note that this method does not check if the item is {@linkplain #isDamageable
	 * damageable}, unlike {@link #isDamaged}.
	 * 
	 * @see #isDamageable
	 * @see #isDamaged
	 * @see #setDamage
	 */
	public int getDamage() {
		return MathHelper.clamp(this.getOrDefault(DataComponentTypes.DAMAGE, 0), 0, this.getMaxDamage());
	}

	/**
	 * Sets the stack's damage to {@code damage}.
	 * 
	 * <p>This does not break the item if the damage reaches {@linkplain #getMaxDamage
	 * the maximum}, unlike {@link #damage(int, LivingEntity, EquipmentSlot)}.
	 * 
	 * @see #getDamage
	 * @see #damage(int, ServerWorld, ServerPlayerEntity, Consumer)
	 * @see #damage(int, LivingEntity, EquipmentSlot)
	 */
	public void setDamage(int damage) {
		this.set(DataComponentTypes.DAMAGE, MathHelper.clamp(damage, 0, this.getMaxDamage()));
	}

	public int getMaxDamage() {
		return this.getOrDefault(DataComponentTypes.MAX_DAMAGE, 0);
	}

	public boolean shouldBreak() {
		return this.isDamageable() && this.getDamage() >= this.getMaxDamage();
	}

	public boolean willBreakNextUse() {
		return this.isDamageable() && this.getDamage() >= this.getMaxDamage() - 1;
	}

	/**
	 * Damages this item stack. This method should be used when a non-entity, such as a
	 * dispenser, damages the stack. This does not damage {@linkplain #isDamageable non-damageable}
	 * stacks, and the enchantments are applied to {@code amount} before damaging.
	 * 
	 * <p>If {@code player} is not {@code null}, this triggers {@link
	 * net.minecraft.advancement.criterion.Criteria#ITEM_DURABILITY_CHANGED}.
	 * 
	 * <p>When the item "breaks", that is, the stack's damage is equal to or above
	 * {@linkplain #getMaxDamage the maximum damage}, {@code breakCallback} is run.
	 * Note that this method automatically decrements the stack size.
	 */
	public void damage(int amount, ServerWorld world, @Nullable ServerPlayerEntity player, Consumer<Item> breakCallback) {
		int i = this.calculateDamage(amount, world, player);
		if (i != 0) {
			this.onDurabilityChange(this.getDamage() + i, player, breakCallback);
		}
	}

	private int calculateDamage(int baseDamage, ServerWorld world, @Nullable ServerPlayerEntity player) {
		if (!this.isDamageable()) {
			return 0;
		} else if (player != null && player.isInCreativeMode()) {
			return 0;
		} else {
			return baseDamage > 0 ? EnchantmentHelper.getItemDamage(world, this, baseDamage) : baseDamage;
		}
	}

	private void onDurabilityChange(int damage, @Nullable ServerPlayerEntity player, Consumer<Item> breakCallback) {
		if (player != null) {
			Criteria.ITEM_DURABILITY_CHANGED.trigger(player, this, damage);
		}

		this.setDamage(damage);
		if (this.shouldBreak()) {
			Item item = this.getItem();
			this.decrement(1);
			breakCallback.accept(item);
		}
	}

	public void damage(int amount, PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayerEntity) {
			int i = this.calculateDamage(amount, serverPlayerEntity.getEntityWorld(), serverPlayerEntity);
			if (i == 0) {
				return;
			}

			int j = Math.min(this.getDamage() + i, this.getMaxDamage() - 1);
			this.onDurabilityChange(j, serverPlayerEntity, item -> {});
		}
	}

	public void damage(int amount, LivingEntity entity, Hand hand) {
		this.damage(amount, entity, hand.getEquipmentSlot());
	}

	/**
	 * Damages this item stack. This method should be used when an entity, including a player,
	 * damages the stack. This does not damage {@linkplain #isDamageable non-damageable}
	 * stacks, and the enchantments are applied to {@code amount} before damaging. Additionally,
	 * if {@code entity} is a player in creative mode, the stack will not be damaged.
	 * 
	 * <p>If {@code entity} is a player, this triggers {@link
	 * net.minecraft.advancement.criterion.Criteria#ITEM_DURABILITY_CHANGED}.
	 * 
	 * <p>If the stack's damage is equal to or above {@linkplain #getMaxDamage the maximum
	 * damage} (i.e. the item is "broken"), this will {@linkplain
	 * LivingEntity#sendEquipmentBreakStatus send the equipment break status}, decrement the
	 * stack, and increment {@link net.minecraft.stat.Stats#BROKEN} if the stack is held
	 * by a player.
	 * 
	 * @param slot the slot in which the stack is held
	 */
	public void damage(int amount, LivingEntity entity, EquipmentSlot slot) {
		if (entity.getEntityWorld() instanceof ServerWorld serverWorld) {
			this.damage(
				amount,
				serverWorld,
				entity instanceof ServerPlayerEntity serverPlayerEntity ? serverPlayerEntity : null,
				item -> entity.sendEquipmentBreakStatus(item, slot)
			);
		}
	}

	public ItemStack damage(int amount, ItemConvertible itemAfterBreaking, LivingEntity entity, EquipmentSlot slot) {
		this.damage(amount, entity, slot);
		if (this.isEmpty()) {
			ItemStack itemStack = this.copyComponentsToNewStackIgnoreEmpty(itemAfterBreaking, 1);
			if (itemStack.isDamageable()) {
				itemStack.setDamage(0);
			}

			return itemStack;
		} else {
			return this;
		}
	}

	public boolean isItemBarVisible() {
		return this.getItem().isItemBarVisible(this);
	}

	/**
	 * {@return the length of the filled section of the durability bar in pixels (out of 13)}
	 */
	public int getItemBarStep() {
		return this.getItem().getItemBarStep(this);
	}

	/**
	 * {@return the color of the filled section of the durability bar}
	 */
	public int getItemBarColor() {
		return this.getItem().getItemBarColor(this);
	}

	public boolean onStackClicked(Slot slot, ClickType clickType, PlayerEntity player) {
		return this.getItem().onStackClicked(this, slot, clickType, player);
	}

	public boolean onClicked(ItemStack stack, Slot slot, ClickType clickType, PlayerEntity player, StackReference cursorStackReference) {
		return this.getItem().onClicked(this, stack, slot, clickType, player, cursorStackReference);
	}

	public boolean postHit(LivingEntity target, LivingEntity user) {
		Item item = this.getItem();
		item.postHit(this, target, user);
		if (this.contains(DataComponentTypes.WEAPON)) {
			if (user instanceof PlayerEntity playerEntity) {
				playerEntity.incrementStat(Stats.USED.getOrCreateStat(item));
			}

			return true;
		} else {
			return false;
		}
	}

	public void postDamageEntity(LivingEntity target, LivingEntity user) {
		this.getItem().postDamageEntity(this, target, user);
		WeaponComponent weaponComponent = this.get(DataComponentTypes.WEAPON);
		if (weaponComponent != null) {
			this.damage(weaponComponent.itemDamagePerAttack(), user, EquipmentSlot.MAINHAND);
		}
	}

	public void postMine(World world, BlockState state, BlockPos pos, PlayerEntity miner) {
		Item item = this.getItem();
		if (item.postMine(this, world, state, pos, miner)) {
			miner.incrementStat(Stats.USED.getOrCreateStat(item));
		}
	}

	/**
	 * Determines whether this item can be used as a suitable tool for mining the specified block.
	 * <p>
	 * Depending on block implementation, when combined together, the correct item and block may achieve a better mining speed and yield
	 * drops that would not be obtained when mining otherwise.
	 * 
	 * @return values consistent with calls to {@link Item#isCorrectForDrops}
	 * @see Item#isCorrectForDrops(ItemStack, BlockState)
	 */
	public boolean isSuitableFor(BlockState state) {
		return this.getItem().isCorrectForDrops(this, state);
	}

	public ActionResult useOnEntity(PlayerEntity user, LivingEntity entity, Hand hand) {
		EquippableComponent equippableComponent = this.get(DataComponentTypes.EQUIPPABLE);
		if (equippableComponent != null && equippableComponent.equipOnInteract()) {
			ActionResult actionResult = equippableComponent.equipOnInteract(user, entity, this);
			if (actionResult != ActionResult.PASS) {
				return actionResult;
			}
		}

		return this.getItem().useOnEntity(this, user, entity, hand);
	}

	/**
	 * {@return a copy of this item stack, including the item count, components, and
	 * {@linkplain #getBobbingAnimationTime bobbing animation time}}
	 * 
	 * @see #copyWithCount
	 * @see #copyComponentsToNewStack
	 * @see #copyComponentsToNewStackIgnoreEmpty
	 */
	public ItemStack copy() {
		if (this.isEmpty()) {
			return EMPTY;
		} else {
			ItemStack itemStack = new ItemStack(this.getItem(), this.count, this.components.copy());
			itemStack.setBobbingAnimationTime(this.getBobbingAnimationTime());
			return itemStack;
		}
	}

	/**
	 * {@return a copy of this item stack, including the components, and {@linkplain #getBobbingAnimationTime bobbing animation time},
	 * with the item count set to {@code count}}
	 * 
	 * @see #copy
	 * @see #copyComponentsToNewStack
	 * @see #copyComponentsToNewStackIgnoreEmpty
	 * 
	 * @param count the item count of the resultant stack
	 */
	public ItemStack copyWithCount(int count) {
		if (this.isEmpty()) {
			return EMPTY;
		} else {
			ItemStack itemStack = this.copy();
			itemStack.setCount(count);
			return itemStack;
		}
	}

	public ItemStack withItem(ItemConvertible item) {
		return this.copyComponentsToNewStack(item, this.getCount());
	}

	/**
	 * {@return a new item stack with the components copied from this item stack}
	 * 
	 * @see #copy
	 * @see #copyWithCount
	 * @see #copyComponentsToNewStackIgnoreEmpty
	 * 
	 * @param item the item of the resultant stack
	 * @param count the item count of the resultant stack
	 */
	public ItemStack copyComponentsToNewStack(ItemConvertible item, int count) {
		return this.isEmpty() ? EMPTY : this.copyComponentsToNewStackIgnoreEmpty(item, count);
	}

	/**
	 * {@return a new item stack with the components copied from this item stack, even if this stack is empty}
	 * 
	 * @see #copy
	 * @see #copyWithCount
	 * @see #copyComponentsToNewStack
	 * 
	 * @param count the item count of the resultant stack
	 * @param item the item of the resultant stack
	 */
	private ItemStack copyComponentsToNewStackIgnoreEmpty(ItemConvertible item, int count) {
		return new ItemStack(item.asItem().getRegistryEntry(), count, this.components.getChanges());
	}

	/**
	 * {@return whether the given item stacks are equal, including the item count and components}
	 * 
	 * @see #areItemsEqual
	 * @see #areItemsAndComponentsEqual
	 */
	public static boolean areEqual(ItemStack left, ItemStack right) {
		if (left == right) {
			return true;
		} else {
			return left.getCount() != right.getCount() ? false : areItemsAndComponentsEqual(left, right);
		}
	}

	@Deprecated
	public static boolean stacksEqual(List<ItemStack> left, List<ItemStack> right) {
		if (left.size() != right.size()) {
			return false;
		} else {
			for (int i = 0; i < left.size(); i++) {
				if (!areEqual((ItemStack)left.get(i), (ItemStack)right.get(i))) {
					return false;
				}
			}

			return true;
		}
	}

	/**
	 * {@return whether the given item stacks contain the same item, regardless of item count or components}
	 * 
	 * @see #areEqual
	 * @see #areItemsAndComponentsEqual
	 */
	public static boolean areItemsEqual(ItemStack left, ItemStack right) {
		return left.isOf(right.getItem());
	}

	/**
	 * {@return whether the given item stacks' items and components are equal}
	 * 
	 * <p>If this returns {@code true}, the two item stacks can be combined into one,
	 * as long as the resulting item count does not exceed {@linkplain Item#getMaxCount
	 * the maximum item count}
	 * 
	 * @see #areEqual
	 * @see #areItemsEqual
	 */
	public static boolean areItemsAndComponentsEqual(ItemStack stack, ItemStack otherStack) {
		if (!stack.isOf(otherStack.getItem())) {
			return false;
		} else {
			return stack.isEmpty() && otherStack.isEmpty() ? true : Objects.equals(stack.components, otherStack.components);
		}
	}

	public static MapCodec<ItemStack> createOptionalCodec(String fieldName) {
		return CODEC.lenientOptionalFieldOf(fieldName)
			.xmap(optional -> (ItemStack)optional.orElse(EMPTY), stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));
	}

	public static int hashCode(@Nullable ItemStack stack) {
		if (stack != null) {
			int i = 31 + stack.getItem().hashCode();
			return 31 * i + stack.getComponents().hashCode();
		} else {
			return 0;
		}
	}

	@Deprecated
	public static int listHashCode(List<ItemStack> stacks) {
		int i = 0;

		for (ItemStack itemStack : stacks) {
			i = i * 31 + hashCode(itemStack);
		}

		return i;
	}

	public String toString() {
		return this.getCount() + " " + this.getItem();
	}

	public void inventoryTick(World world, Entity entity, @Nullable EquipmentSlot slot) {
		if (this.bobbingAnimationTime > 0) {
			this.bobbingAnimationTime--;
		}

		if (world instanceof ServerWorld serverWorld) {
			this.getItem().inventoryTick(this, serverWorld, entity, slot);
		}
	}

	public void onCraftByPlayer(PlayerEntity player, int amount) {
		player.increaseStat(Stats.CRAFTED.getOrCreateStat(this.getItem()), amount);
		this.getItem().onCraftByPlayer(this, player);
	}

	public void onCraftByCrafter(World world) {
		this.getItem().onCraft(this, world);
	}

	public int getMaxUseTime(LivingEntity user) {
		return this.getItem().getMaxUseTime(this, user);
	}

	public UseAction getUseAction() {
		return this.getItem().getUseAction(this);
	}

	public void onStoppedUsing(World world, LivingEntity user, int remainingUseTicks) {
		ItemStack itemStack = this.copy();
		if (this.getItem().onStoppedUsing(this, world, user, remainingUseTicks)) {
			ItemStack itemStack2 = this.applyRemainderAndCooldown(user, itemStack);
			if (itemStack2 != this) {
				user.setStackInHand(user.getActiveHand(), itemStack2);
			}
		}
	}

	public boolean isUsedOnRelease() {
		return this.getItem().isUsedOnRelease(this);
	}

	/**
	 * Sets the component {@code type} for this item stack to {@code value}.
	 * 
	 * <p>If {@code value} is {@code null}, the component is removed and the base component
	 * is unset. To reverse the stack-specific change, instead pass the default value
	 * as {@code value}.
	 * 
	 * @return the previous value set
	 * @see #apply(DataComponentType, Object, UnaryOperator)
	 * @see #apply(DataComponentType, Object, Object, BiFunction)
	 */
	@Nullable
	public <T> T set(ComponentType<T> type, @Nullable T value) {
		return this.components.set(type, value);
	}

	@Nullable
	public <T> T set(Component<T> component) {
		return this.components.set(component);
	}

	public <T> void copy(ComponentType<T> type, ComponentsAccess from) {
		this.set(type, from.get(type));
	}

	/**
	 * Sets the component {@code type} by passing the current value and {@code change}
	 * to {@code applier}, then setting its return value as the value. If the component is
	 * missing, {@code defaultValue} is used as the default.
	 * 
	 * <p>In practice, {@code applier} is a reference to a method of the component
	 * class with one parameter, that returns a new instance of the component with the
	 * specific value changed to {@code change}. For example, adding a lore can be accomplished
	 * by passing reference to {@link net.minecraft.component.type.LoreComponent#with}
	 * and the added lore, like
	 * {@code stack.apply(DataComponentTypes.LORE, LoreComponent.DEFAULT, text, LoreComponent::with)}.
	 * 
	 * @implNote This is the same as setting {@code applier.apply(stack.getOrDefault(type, defaultValue), change)}.
	 * 
	 * @return the previous value set
	 * @see #apply(DataComponentType, Object, UnaryOperator)
	 * @see #set
	 */
	@Nullable
	public <T, U> T apply(ComponentType<T> type, T defaultValue, U change, BiFunction<T, U, T> applier) {
		return this.set(type, (T)applier.apply(this.getOrDefault(type, defaultValue), change));
	}

	/**
	 * Sets the component {@code type} by passing the current value (or {@code defaultValue}
	 * if the component is missing) to {@code applier} and then setting its return value as
	 * the value.
	 * 
	 * @implNote This is the same as setting {@code applier.apply(stack.getOrDefault(type, defaultValue))}.
	 * 
	 * @return the previous value set
	 * @see #set
	 * @see #apply(DataComponentType, Object, Object, BiFunction)
	 */
	@Nullable
	public <T> T apply(ComponentType<T> type, T defaultValue, UnaryOperator<T> applier) {
		T object = this.getOrDefault(type, defaultValue);
		return this.set(type, (T)applier.apply(object));
	}

	/**
	 * Removes the component {@code type}. If it is in the stack's base component,
	 * it is unset and the component becomes missing. To reverse the stack-specific change,
	 * instead pass the default value as {@code value}.
	 * 
	 * @return the previous value set
	 */
	@Nullable
	public <T> T remove(ComponentType<? extends T> type) {
		return this.components.remove(type);
	}

	public void applyChanges(ComponentChanges changes) {
		ComponentChanges componentChanges = this.components.getChanges();
		this.components.applyChanges(changes);
		Optional<Error<ItemStack>> optional = validate(this).error();
		if (optional.isPresent()) {
			LOGGER.error("Failed to apply component patch '{}' to item: '{}'", changes, ((Error)optional.get()).message());
			this.components.setChanges(componentChanges);
		}
	}

	public void applyUnvalidatedChanges(ComponentChanges changes) {
		this.components.applyChanges(changes);
	}

	public void applyComponentsFrom(ComponentMap components) {
		this.components.setAll(components);
	}

	/**
	 * {@return the custom name of the stack if it exists, or the item's name}
	 */
	public Text getName() {
		Text text = this.getCustomName();
		return text != null ? text : this.getItemName();
	}

	/**
	 * {@return the custom name or book title of the stack if it exists, or {@code null}}
	 */
	@Nullable
	public Text getCustomName() {
		Text text = this.get(DataComponentTypes.CUSTOM_NAME);
		if (text != null) {
			return text;
		} else {
			WrittenBookContentComponent writtenBookContentComponent = this.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
			if (writtenBookContentComponent != null) {
				String string = writtenBookContentComponent.title().raw();
				if (!StringHelper.isBlank(string)) {
					return Text.literal(string);
				}
			}

			return null;
		}
	}

	public Text getItemName() {
		return this.getItem().getName(this);
	}

	public Text getFormattedName() {
		MutableText mutableText = Text.empty().append(this.getName()).formatted(this.getRarity().getFormatting());
		if (this.contains(DataComponentTypes.CUSTOM_NAME)) {
			mutableText.formatted(Formatting.ITALIC);
		}

		return mutableText;
	}

	public <T extends TooltipAppender> void appendComponentTooltip(
		ComponentType<T> componentType, Item.TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> textConsumer, TooltipType type
	) {
		T tooltipAppender = (T)this.get(componentType);
		if (tooltipAppender != null && displayComponent.shouldDisplay(componentType)) {
			tooltipAppender.appendTooltip(context, textConsumer, type, this.components);
		}
	}

	public List<Text> getTooltip(Item.TooltipContext context, @Nullable PlayerEntity player, TooltipType type) {
		TooltipDisplayComponent tooltipDisplayComponent = this.getOrDefault(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT);
		if (!type.isCreative() && tooltipDisplayComponent.hideTooltip()) {
			boolean bl = this.getItem().shouldShowOperatorBlockWarnings(this, player);
			return bl ? OPERATOR_WARNINGS : List.of();
		} else {
			List<Text> list = Lists.<Text>newArrayList();
			list.add(this.getFormattedName());
			this.appendTooltip(context, tooltipDisplayComponent, player, type, list::add);
			return list;
		}
	}

	public void appendTooltip(
		Item.TooltipContext context, TooltipDisplayComponent displayComponent, @Nullable PlayerEntity player, TooltipType type, Consumer<Text> textConsumer
	) {
		this.getItem().appendTooltip(this, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.TROPICAL_FISH_PATTERN, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.INSTRUMENT, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.MAP_ID, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.BEES, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.CONTAINER_LOOT, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.CONTAINER, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.BANNER_PATTERNS, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.POT_DECORATIONS, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.WRITTEN_BOOK_CONTENT, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.CHARGED_PROJECTILES, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.FIREWORKS, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.FIREWORK_EXPLOSION, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.POTION_CONTENTS, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.JUKEBOX_PLAYABLE, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.TRIM, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.STORED_ENCHANTMENTS, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.ENCHANTMENTS, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.DYED_COLOR, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.PROFILE, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.LORE, context, displayComponent, textConsumer, type);
		this.appendAttributeModifiersTooltip(textConsumer, displayComponent, player);
		if (this.contains(DataComponentTypes.UNBREAKABLE) && displayComponent.shouldDisplay(DataComponentTypes.UNBREAKABLE)) {
			textConsumer.accept(UNBREAKABLE_TEXT);
		}

		this.appendComponentTooltip(DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.BLOCK_STATE, context, displayComponent, textConsumer, type);
		this.appendComponentTooltip(DataComponentTypes.ENTITY_DATA, context, displayComponent, textConsumer, type);
		if ((this.isOf(Items.SPAWNER) || this.isOf(Items.TRIAL_SPAWNER)) && displayComponent.shouldDisplay(DataComponentTypes.BLOCK_ENTITY_DATA)) {
			TypedEntityData<BlockEntityType<?>> typedEntityData = this.get(DataComponentTypes.BLOCK_ENTITY_DATA);
			Spawner.appendSpawnDataToTooltip(typedEntityData, textConsumer, "SpawnData");
		}

		BlockPredicatesComponent blockPredicatesComponent = this.get(DataComponentTypes.CAN_BREAK);
		if (blockPredicatesComponent != null && displayComponent.shouldDisplay(DataComponentTypes.CAN_BREAK)) {
			textConsumer.accept(ScreenTexts.EMPTY);
			textConsumer.accept(BlockPredicatesComponent.CAN_BREAK_TEXT);
			blockPredicatesComponent.addTooltips(textConsumer);
		}

		BlockPredicatesComponent blockPredicatesComponent2 = this.get(DataComponentTypes.CAN_PLACE_ON);
		if (blockPredicatesComponent2 != null && displayComponent.shouldDisplay(DataComponentTypes.CAN_PLACE_ON)) {
			textConsumer.accept(ScreenTexts.EMPTY);
			textConsumer.accept(BlockPredicatesComponent.CAN_PLACE_TEXT);
			blockPredicatesComponent2.addTooltips(textConsumer);
		}

		if (type.isAdvanced()) {
			if (this.isDamaged() && displayComponent.shouldDisplay(DataComponentTypes.DAMAGE)) {
				textConsumer.accept(Text.translatable("item.durability", this.getMaxDamage() - this.getDamage(), this.getMaxDamage()));
			}

			textConsumer.accept(Text.literal(Registries.ITEM.getId(this.getItem()).toString()).formatted(Formatting.DARK_GRAY));
			int i = this.components.size();
			if (i > 0) {
				textConsumer.accept(Text.translatable("item.components", i).formatted(Formatting.DARK_GRAY));
			}
		}

		if (player != null && !this.getItem().isEnabled(player.getEntityWorld().getEnabledFeatures())) {
			textConsumer.accept(DISABLED_TEXT);
		}

		boolean bl = this.getItem().shouldShowOperatorBlockWarnings(this, player);
		if (bl) {
			OPERATOR_WARNINGS.forEach(textConsumer);
		}
	}

	private void appendAttributeModifiersTooltip(Consumer<Text> textConsumer, TooltipDisplayComponent displayComponent, @Nullable PlayerEntity player) {
		if (displayComponent.shouldDisplay(DataComponentTypes.ATTRIBUTE_MODIFIERS)) {
			for (AttributeModifierSlot attributeModifierSlot : AttributeModifierSlot.values()) {
				MutableBoolean mutableBoolean = new MutableBoolean(true);
				this.applyAttributeModifier(attributeModifierSlot, (attribute, modifier, display) -> {
					if (display != AttributeModifiersComponent.Display.getHidden()) {
						if (mutableBoolean.isTrue()) {
							textConsumer.accept(ScreenTexts.EMPTY);
							textConsumer.accept(Text.translatable("item.modifiers." + attributeModifierSlot.asString()).formatted(Formatting.GRAY));
							mutableBoolean.setFalse();
						}

						display.addTooltip(textConsumer, player, attribute, modifier);
					}
				});
			}
		}
	}

	public boolean hasGlint() {
		Boolean boolean_ = this.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
		return boolean_ != null ? boolean_ : this.getItem().hasGlint(this);
	}

	public Rarity getRarity() {
		Rarity rarity = this.getOrDefault(DataComponentTypes.RARITY, Rarity.COMMON);
		if (!this.hasEnchantments()) {
			return rarity;
		} else {
			return switch (rarity) {
				case COMMON, UNCOMMON -> Rarity.RARE;
				case RARE -> Rarity.EPIC;
				default -> rarity;
			};
		}
	}

	/**
	 * {@return whether this item stack can be enchanted with an enchanting table}
	 * 
	 * <p>This is not used for other methods of enchanting like anvils.
	 */
	public boolean isEnchantable() {
		if (!this.contains(DataComponentTypes.ENCHANTABLE)) {
			return false;
		} else {
			ItemEnchantmentsComponent itemEnchantmentsComponent = this.get(DataComponentTypes.ENCHANTMENTS);
			return itemEnchantmentsComponent != null && itemEnchantmentsComponent.isEmpty();
		}
	}

	/**
	 * Enchants this item with the given enchantment and level.
	 * 
	 * <p>This should not be used with enchanted books, as the book itself is not
	 * enchanted and therefore does not store enchantments under {@link
	 * net.minecraft.component.DataComponentTypes#ENCHANTMENTS}.
	 * 
	 * @see net.minecraft.enchantment.EnchantmentHelper
	 */
	public void addEnchantment(RegistryEntry<Enchantment> enchantment, int level) {
		EnchantmentHelper.apply(this, builder -> builder.add(enchantment, level));
	}

	/**
	 * {@return whether the item stack has any enchantments}
	 * 
	 * <p>This will return {@code false} for enchanted books, as the book itself is not
	 * enchanted and therefore does not store enchantments under {@link
	 * net.minecraft.component.DataComponentTypes#ENCHANTMENTS}.
	 * 
	 * @see net.minecraft.enchantment.EnchantmentHelper#getEnchantments
	 */
	public boolean hasEnchantments() {
		return !this.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).isEmpty();
	}

	public ItemEnchantmentsComponent getEnchantments() {
		return this.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
	}

	/**
	 * {@return whether the item stack is in an item frame}
	 * 
	 * @see #setHolder
	 * @see #getFrame
	 * @see #getHolder
	 */
	public boolean isInFrame() {
		return this.holder instanceof ItemFrameEntity;
	}

	/**
	 * Sets the stack's holder to {@code holder}.
	 * 
	 * <p>This is used by item frames and item entities, and does not need to be called
	 * for other entities.
	 * 
	 * @see #isInFrame
	 * @see #getFrame
	 * @see #getHolder
	 */
	public void setHolder(@Nullable Entity holder) {
		if (!this.isEmpty()) {
			this.holder = holder;
		}
	}

	/**
	 * {@return the item frame that holds the stack, or {@code null} if inapplicable}
	 * 
	 * @see #isInFrame
	 * @see #setHolder
	 * @see #getHolder
	 */
	@Nullable
	public ItemFrameEntity getFrame() {
		return this.holder instanceof ItemFrameEntity ? (ItemFrameEntity)this.getHolder() : null;
	}

	/**
	 * {@return the entity that holds the stack, or {@code null} if inapplicable}
	 * 
	 * @see #isInFrame
	 * @see #getFrame
	 * @see #setHolder
	 */
	@Nullable
	public Entity getHolder() {
		return !this.isEmpty() ? this.holder : null;
	}

	public void applyAttributeModifier(
		AttributeModifierSlot slot,
		TriConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier, AttributeModifiersComponent.Display> attributeModifierConsumer
	) {
		AttributeModifiersComponent attributeModifiersComponent = this.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
		attributeModifiersComponent.applyModifiers(slot, attributeModifierConsumer);
		EnchantmentHelper.applyAttributeModifiers(
			this, slot, (attribute, modifier) -> attributeModifierConsumer.accept(attribute, modifier, AttributeModifiersComponent.Display.getDefault())
		);
	}

	public void applyAttributeModifiers(EquipmentSlot slot, BiConsumer<RegistryEntry<EntityAttribute>, EntityAttributeModifier> attributeModifierConsumer) {
		AttributeModifiersComponent attributeModifiersComponent = this.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
		attributeModifiersComponent.applyModifiers(slot, attributeModifierConsumer);
		EnchantmentHelper.applyAttributeModifiers(this, slot, attributeModifierConsumer);
	}

	/**
	 * {@return a text consisting of the bracketed {@linkplain #getName stack name} that
	 * can be hovered to show the item stack's tooltip}
	 */
	public Text toHoverableText() {
		MutableText mutableText = Text.empty().append(this.getName());
		if (this.contains(DataComponentTypes.CUSTOM_NAME)) {
			mutableText.formatted(Formatting.ITALIC);
		}

		MutableText mutableText2 = Texts.bracketed(mutableText);
		if (!this.isEmpty()) {
			mutableText2.formatted(this.getRarity().getFormatting()).styled(style -> style.withHoverEvent(new HoverEvent.ShowItem(this)));
		}

		return mutableText2;
	}

	public boolean canPlaceOn(CachedBlockPosition pos) {
		BlockPredicatesComponent blockPredicatesComponent = this.get(DataComponentTypes.CAN_PLACE_ON);
		return blockPredicatesComponent != null && blockPredicatesComponent.check(pos);
	}

	public boolean canBreak(CachedBlockPosition pos) {
		BlockPredicatesComponent blockPredicatesComponent = this.get(DataComponentTypes.CAN_BREAK);
		return blockPredicatesComponent != null && blockPredicatesComponent.check(pos);
	}

	public int getBobbingAnimationTime() {
		return this.bobbingAnimationTime;
	}

	public void setBobbingAnimationTime(int bobbingAnimationTime) {
		this.bobbingAnimationTime = bobbingAnimationTime;
	}

	/**
	 * {@return the count of items in this item stack}
	 */
	public int getCount() {
		return this.isEmpty() ? 0 : this.count;
	}

	/**
	 * Sets the count of items in this item stack.
	 * 
	 * @param count the count of items
	 */
	public void setCount(int count) {
		this.count = count;
	}

	/**
	 * Sets the count of items in this item stack to not exceed {@code maxCount}.
	 */
	public void capCount(int maxCount) {
		if (!this.isEmpty() && this.getCount() > maxCount) {
			this.setCount(maxCount);
		}
	}

	/**
	 * Increments the count of items in this item stack.
	 * 
	 * @param amount the amount to increment
	 */
	public void increment(int amount) {
		this.setCount(this.getCount() + amount);
	}

	/**
	 * Decrements the count of items in this item stack.
	 * 
	 * @param amount the amount to decrement
	 */
	public void decrement(int amount) {
		this.increment(-amount);
	}

	/**
	 * Decrements the count of items in this item stack, unless {@code entity}
	 * is a creative mode player.
	 */
	public void decrementUnlessCreative(int amount, @Nullable LivingEntity entity) {
		if (entity == null || !entity.isInCreativeMode()) {
			this.decrement(amount);
		}
	}

	public ItemStack splitUnlessCreative(int amount, @Nullable LivingEntity entity) {
		ItemStack itemStack = this.copyWithCount(amount);
		this.decrementUnlessCreative(amount, entity);
		return itemStack;
	}

	public void usageTick(World world, LivingEntity user, int remainingUseTicks) {
		ConsumableComponent consumableComponent = this.get(DataComponentTypes.CONSUMABLE);
		if (consumableComponent != null && consumableComponent.shouldSpawnParticlesAndPlaySounds(remainingUseTicks)) {
			consumableComponent.spawnParticlesAndPlaySound(user.getRandom(), user, this, 5);
		}

		this.getItem().usageTick(world, user, this, remainingUseTicks);
	}

	public void onItemEntityDestroyed(ItemEntity entity) {
		this.getItem().onItemEntityDestroyed(entity);
	}

	public boolean takesDamageFrom(DamageSource source) {
		DamageResistantComponent damageResistantComponent = this.get(DataComponentTypes.DAMAGE_RESISTANT);
		return damageResistantComponent == null || !damageResistantComponent.resists(source);
	}

	public boolean canRepairWith(ItemStack ingredient) {
		RepairableComponent repairableComponent = this.get(DataComponentTypes.REPAIRABLE);
		return repairableComponent != null && repairableComponent.matches(ingredient);
	}

	public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity player) {
		return this.getItem().canMine(this, state, world, pos, player);
	}
}
