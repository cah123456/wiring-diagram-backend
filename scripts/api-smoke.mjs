import assert from 'node:assert/strict'

const BASE = process.env.API_BASE ?? 'http://localhost:8080/api'
const createdIds = []
let passed = 0

const call = async (method, path, body) => {
  const options = { method, headers: {} }
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json'
    options.body = typeof body === 'string' ? body : JSON.stringify(body)
  }
  const response = await fetch(BASE + path, options)
  const text = await response.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = null
  }
  return { status: response.status, json, text }
}

const record = (name) => {
  passed += 1
  console.log(`PASS ${name}`)
}

const expectEnvelope = (result, status, success, name) => {
  assert.equal(result.status, status, `${name}: HTTP ${result.status}`)
  assert.ok(result.json, `${name}: 响应不是 JSON`)
  assert.equal(result.json.success, success, `${name}: success 不符`)
  assert.equal(result.json.code, success ? 20000 : 20001, `${name}: code 不符`)
  assert.equal(typeof result.json.message, 'string', `${name}: message 缺失`)
  assert.ok(result.json.data && typeof result.json.data === 'object', `${name}: data 缺失`)
  return result.json
}

const cleanup = async () => {
  for (const id of createdIds) {
    try {
      await call('DELETE', `/diagrams/${id}`)
    } catch {
      // 清理失败不影响测试结论，人工可再次删除。
    }
  }
}

const run = async () => {
  const suffix = Date.now().toString(36).toUpperCase()
  const name = `简化版冒烟-${suffix}`

  try {
    const list = await call('GET', '/diagrams?pageNum=1&pageSize=5')
    const listBody = expectEnvelope(list, 200, true, '列表')
    assert.ok(Array.isArray(listBody.data.result.records), '列表 result.records 不是数组')
    assert.equal(typeof listBody.data.result.total, 'number', '列表 total 不是数字')
    assert.equal(listBody.data.result.current, 1, '列表 current 应为 1')
    record('GET /diagrams 返回 R + IPage 分页数据')

    const stats = await call('GET', '/diagrams/stats')
    const statsBody = expectEnvelope(stats, 200, true, '统计')
    assert.equal(typeof statsBody.data.result.diagramCount, 'number', '统计 diagramCount 不是数字')
    record('GET /diagrams/stats 返回 R + 统计数据')

    const validation = await call('POST', '/diagrams', { name: '' })
    const validationBody = expectEnvelope(validation, 400, false, '校验失败')
    assert.equal(validationBody.message, '提交的数据校验未通过', '校验失败 message 不符')
    assert.equal(validationBody.data.fields.name, '图纸名称不能为空', '校验字段提示不符')
    record('参数校验失败返回 400 + data.fields')

    const create = await call('POST', '/diagrams', {
      name,
      voltageLevel: '10kV',
      description: 'MyBatis-Plus 简化版冒烟测试，测试结束会自动删除',
    })
    const createBody = expectEnvelope(create, 201, true, '创建')
    const id = createBody.data.result.id
    createdIds.push(id)
    assert.ok(id > 0, '创建结果缺少 id')
    assert.equal(createBody.message, '图纸创建成功', '创建提示语不符')
    assert.equal(createBody.data.result.revision, undefined, '简化后不应再返回 revision')
    assert.ok(Array.isArray(createBody.data.result.document.nodes), '默认文档缺少 nodes')
    assert.equal(createBody.data.result.document.nodes.length, 0, '默认文档 nodes 应为空')
    record('POST /diagrams 返回 201 + data.result + 默认空白文档')

    const detail = await call('GET', `/diagrams/${id}`)
    const detailBody = expectEnvelope(detail, 200, true, '详情')
    assert.equal(detailBody.data.result.id, id, '详情 id 不符')
    record('GET /diagrams/{id} 返回 R + 图纸详情')

    const meta = await call('PATCH', `/diagrams/${id}`, {
      name: `${name}-已改名`,
      voltageLevel: '0.4kV',
      description: '元信息已修改',
    })
    const metaBody = expectEnvelope(meta, 200, true, '改元信息')
    assert.equal(metaBody.data.result.name, `${name}-已改名`, '改名未生效')
    assert.equal(metaBody.data.result.voltageLevel, '0.4kV', '电压等级未生效')
    record('PATCH /diagrams/{id} 修改元信息')

    const document = {
      schemaVersion: 1,
      paper: { size: 'A3', orientation: 'landscape', width: 1600, height: 1131 },
      settings: { gridSize: 20, snapToGrid: true, showGrid: true, showTitleBlock: true },
      meta: { title: name, drawingNo: 'SMOKE-01' },
      nodes: [
        {
          id: 'n1',
          type: 'mccb',
          x: 120,
          y: 160,
          width: 80,
          height: 120,
          rotation: 0,
        },
      ],
      edges: [],
    }

    const save = await call('PUT', `/diagrams/${id}`, { document })
    const saveBody = expectEnvelope(save, 200, true, '保存')
    assert.equal(saveBody.data.result.document.nodes.length, 1, '保存后节点数不符')
    assert.equal(saveBody.data.result.revision, undefined, '保存结果不应包含 revision')
    record('PUT /diagrams/{id} 保存成功')

    const invalid = await call('PUT', `/diagrams/${id}`, { document: {} })
    const invalidBody = expectEnvelope(invalid, 400, false, '非法文档')
    assert.equal(invalidBody.message, '图纸内容缺少 nodes 或 edges 数组', '非法文档提示不符')
    record('缺少 nodes/edges 返回 400 + R 错误体')

    const duplicate = await call('POST', `/diagrams/${id}/duplicate`)
    const duplicateBody = expectEnvelope(duplicate, 201, true, '复制')
    const copyId = duplicateBody.data.result.id
    createdIds.push(copyId)
    assert.notEqual(copyId, id, '副本 id 不应与原图相同')
    assert.equal(duplicateBody.data.result.document.nodes.length, 1, '副本文档未深拷贝')
    record('POST /diagrams/{id}/duplicate 返回 201 + 副本详情')

    const noVersions = await call('GET', `/diagrams/${id}/versions`)
    expectEnvelope(noVersions, 404, false, '版本接口已移除')
    record('版本接口已移除并返回 404')

    const deleted = await call('DELETE', `/diagrams/${id}`)
    const deletedBody = expectEnvelope(deleted, 200, true, '删除')
    assert.equal(deletedBody.message, '图纸已删除', '删除提示语不符')
    assert.equal(deletedBody.data.result, undefined, '删除响应不应包含 result')
    record('DELETE /diagrams/{id} 返回 200 + R 提示语')

    const deletedCopy = await call('DELETE', `/diagrams/${copyId}`)
    expectEnvelope(deletedCopy, 200, true, '删除副本')

    const gone = await call('GET', `/diagrams/${id}`)
    const goneBody = expectEnvelope(gone, 404, false, '已删除详情')
    assert.ok(goneBody.message.includes('不存在'), '已删除提示语不符')
    record('物理删除后查询返回 404 + R 错误体')

    const notFound = await call('GET', '/diagrams/999999999')
    const notFoundBody = expectEnvelope(notFound, 404, false, '不存在图纸')
    assert.ok(notFoundBody.message.includes('不存在'), '不存在提示语不符')
    record('不存在图纸返回 404 + R 错误体')

    const unknown = await call('GET', '/not-a-real-endpoint')
    const unknownBody = expectEnvelope(unknown, 404, false, '未知接口')
    assert.equal(unknownBody.message, '请求的接口不存在', '未知接口提示语不符')
    record('未知接口返回 404 + R 错误体')

    const badJson = await call('POST', '/diagrams', '{not-json')
    const badJsonBody = expectEnvelope(badJson, 400, false, '非法 JSON')
    assert.ok(badJsonBody.message.includes('JSON'), '非法 JSON 提示语不符')
    record('非法 JSON 请求体返回 400 + R 错误体')

    console.log(`\n全部通过：${passed} 项接口断言`)
  } finally {
    await cleanup()
  }
}

run().catch((error) => {
  console.error(`\nFAIL ${error.message}`)
  cleanup().finally(() => process.exit(1))
})
