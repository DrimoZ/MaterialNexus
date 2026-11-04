// MNX-076: unification done by hand, as a pack may have before installing Material Nexus. The GameTest
// scriptDecisionsAreRead (-Pkubejs) expects Material Nexus to read both decisions back (MNX-143: on the 26.1.2 mods).
ServerEvents.recipes(event => {
  event.replaceOutput({ output: 'oritech:copper_dust' }, 'oritech:copper_dust', 'occultism:copper_dust')
})

ServerEvents.tags('item', event => {
  event.remove('c:dusts/iron', ['oritech:iron_dust', 'enderio:powdered_iron'])
})
