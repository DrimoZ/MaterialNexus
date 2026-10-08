// MNX-076: unification done by hand, as a pack may have before installing Material Nexus. The GameTest
// scriptDecisionsAreRead (-Pkubejs) expects Material Nexus to read both decisions back.
ServerEvents.recipes(event => {
  event.replaceOutput({ output: 'mekanism:ingot_tin' }, 'mekanism:ingot_tin', 'thermal:tin_ingot')
})

ServerEvents.tags('item', event => {
  event.remove('forge:dusts/nickel', ['gtceu:nickel_dust', 'immersiveengineering:dust_nickel'])
})
