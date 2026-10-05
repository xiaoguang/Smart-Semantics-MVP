<template>
  <button type="button" @click="choose">Choose</button>
</template>

<script>
export default {
  name: 'ChoicePanel',
  methods: {
    choose() {
      const rows = ['first']
      const selectedId = 'canvas-1'
      this.$emit('chosen', rows, selectedId, 'panel-source')
    },
  },
}
</script>
