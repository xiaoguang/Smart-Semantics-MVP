<template>
  <section>
    <choice-panel ref="panel" @chosen="onChosen"></choice-panel>
    <button type="button" @click="handleOkOnly">Save</button>
    <button type="button" @click="handleOkAndCheck">Save and check</button>
  </section>
</template>

<script>
import ChoicePanel from '../components/ChoicePanel.vue'
import { CanvasEditorMixin } from '../mixins/CanvasEditorMixin.js'

export default {
  name: 'CanvasPage',
  components: { ChoicePanel },
  mixins: [CanvasEditorMixin],
  data() {
    return {
      url: {
        add: '/canvas/record/add',
        edit: '/canvas/record/edit',
      },
      model: { id: null },
      selected: null,
    }
  },
  methods: {
    onChosen(first, second) {
      this.selected = second
    },
    classifyIntoFormData(allValues) {
      return { rows: allValues.rows }
    },
  },
}
</script>
