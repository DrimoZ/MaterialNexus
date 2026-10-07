// MNX-067: the Material Nexus bindings answer from a recipe event (checked by the -Pkubejs GameTest run's log).
ServerEvents.recipes(event => {
  console.info(`MaterialNexus bindings: copper ingot kept=${MaterialNexus.kept('minecraft:copper_ingot')}, conversions=${MaterialNexus.conversions().size()}`)
})
