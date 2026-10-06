<template>
  <button type="button" @click="forwardSelection">Choose</button>
</template>

<script>
import { QueryMixin } from '../mixins/QueryMixin.js'

export default {
  name: 'RecordPicker',
  mixins: [QueryMixin],
  data() {
    return { url: { list: '/records/list' } }
  },
  methods: {
    open(kind, source) {
      this.loadData(1)
    },
    forwardSelection(selectedRows) {
      const selectedId = selectedRows[0].id
      this.$emit('chosen', selectedRows, selectedId, 'fixture-source')
      this.$nextTick(() => {
        this.ready = true
      })
    },
  },
}
</script>
