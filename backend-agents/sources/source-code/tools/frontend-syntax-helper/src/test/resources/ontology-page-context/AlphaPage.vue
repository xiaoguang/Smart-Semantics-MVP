<template>
  <section>
    <record-picker ref="picker" @chosen="onChosen" />
    <record-picker ref="secondaryPicker" @chosen="onSecondaryChosen" />
    <button type="button" @click="loadRecords">Load</button>
    <button type="button" @click="saveRecord">Save</button>
  </section>
</template>

<script>
import RecordPicker from '../components/RecordPicker.vue'
import { saveAction } from '../api/client.js'

export default {
  name: 'AlphaPage',
  components: { RecordPicker },
  data() {
    return {
      url: {
        create: '/records/alpha/create',
        update: '/records/alpha/update',
      },
      form: { id: null, selectedId: null },
    }
  },
  methods: {
    loadRecords() {
      this.$refs.picker.open('alpha')
    },
    loadSecondaryRecords() {
      this.$refs.secondaryPicker.open('alpha-secondary')
    },
    onChosen(rows, selectedId) {
      this.form.selectedId = selectedId
    },
    onSecondaryChosen(rows, secondaryId) {
      this.form.selectedId = secondaryId
    },
    saveRecord() {
      const payload = { selectedId: this.form.selectedId }
      return Promise.resolve(payload).then((value) => this.persistRecord(value))
    },
    persistRecord(payload) {
      if (this.form.id) {
        return saveAction(this.url.update, 'PUT', payload, 'audit-extra')
      }
      return saveAction(this.url.create, 'POST')
    },
  },
}
</script>
