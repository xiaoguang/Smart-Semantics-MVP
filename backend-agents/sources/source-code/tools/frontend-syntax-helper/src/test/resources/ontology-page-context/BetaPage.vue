<template>
  <section>
    <record-picker ref="picker" @chosen="onChosen" />
    <button type="button" @click="loadRecords">Load</button>
    <button type="button" @click="saveRecord">Save</button>
  </section>
</template>

<script>
import RecordPicker from '../components/RecordPicker.vue'
import { saveAction } from '../api/client.js'

export default {
  name: 'BetaPage',
  components: { RecordPicker },
  data() {
    return {
      url: {
        create: '/records/beta/create',
        update: '/records/beta/update',
      },
      form: { id: null, selectedId: null },
    }
  },
  methods: {
    loadRecords() {
      this.$refs.picker.open('beta')
    },
    onChosen(entries, betaId) {
      this.form.selectedId = betaId
    },
    saveRecord() {
      const payload = { selectedId: this.form.selectedId }
      return Promise.resolve(payload).then((value) => this.persistRecord(value))
    },
    persistRecord(payload) {
      if (this.form.id) {
        return saveAction(this.url.update, 'PUT', payload)
      }
      return saveAction(this.url.create, 'POST', payload)
    },
  },
}
</script>
