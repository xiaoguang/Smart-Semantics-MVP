import { httpAction } from '../api/actionClient.js'

export const CanvasEditorMixin = {
  methods: {
    handleOkOnly() {
      this.status = 'draft'
      this.handleOk()
    },
    handleOkAndCheck() {
      this.status = 'checked'
      this.handleOk()
    },
    handleOk() {
      return this.getAllTable().then((allValues) => {
        const formData = this.classifyIntoFormData(allValues)
        return this.request(formData)
      })
    },
    request(formData) {
      let url = this.url.add
      let method = 'POST'
      if (this.model.id) {
        url = this.url.edit
        method = 'PUT'
      }
      return httpAction(url, formData, method)
    },
  },
}
