<#--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->

<#include "*/generic.ftl">
<#macro page_head>
  <script src="/static/js/jquery.form.js"></script>

  <!-- Ace Libraries for Syntax Formatting -->
  <script src="/static/js/ace-code-editor/ace.js" type="text/javascript" charset="utf-8"></script>
  <script src="/static/js/ace-code-editor/theme-eclipse.js" type="text/javascript" charset="utf-8"></script>
  <script src="/static/js/serverMessage.js"></script>
</#macro>

<#macro page_body>

  <#include "*/confirmationModals.ftl">

  <h4 class="col-xs-6 mx-3">User Credential Management</h4>

  <div class="pb-2 mt-4 mb-2 border-bottom" style="margin: 5px;"></div>

  <div class="container-fluid">
    <div class="row">
      <div class="table-responsive col-sm-12 col-md-6 col-lg-5 col-xl-5">
        <h4>Enabled Storage Plugins</h4>
        <table class="table table-hover">
          <tbody>
  <#list model as pluginModel>
                <tr>
                  <td style="border:none; max-width: 200px; overflow: hidden; text-overflow: ellipsis;">
      ${pluginModel.getPlugin().getName()}
                  </td>
                  <td style="border:none;">
                    <button type="button" class="btn btn-primary" data-toggle="modal" data-target="#new-plugin-modal">
      Update Credentials
                    </button>
                  </td>
  <td>
    <h3>Creds</h3>
    ${pluginModel.getPlugin().getName()} <br>
    ${pluginModel.getPlugin().getUserName()} <br>
    ${pluginModel.getPlugin().getPassword()} <br>
  </td>
                </tr>
  </#list>
          </tbody>
        </table>
      </div>

<!-- TODO Get model to modal -- >

<#--onclick="doUpdate('${pluginModel.getPlugin().getName()}')"-->
<#-- Modal window for creating plugin -->
  <div class="modal fade" id="new-plugin-modal" role="dialog" aria-labelledby="configuration">
    <div class="modal-dialog" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h4 class="modal-title" id="configuration">Update Credentials</h4>
          <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
        </div>
        <div class="modal-body">

          <form id="createForm" role="form" action="/storage/create_update" method="POST">
            <input type="text" class="form-control" name="username" placeholder="Username" />
            <input type="text" class="form-control" name="password" placeholder="Password" />
            <div style="text-align: right; margin: 10px">
              <button type="button" class="btn btn-secondary" data-dismiss="modal">Close</button>
              <button type="submit" class="btn btn-primary" onclick="doCreate()">Create</button>
            </div>
            <input type="hidden" name="csrfToken" value="${model[0].getCsrfToken()}">
          </form>

          <div id="message" class="d-none alert alert-info">
          </div>
        </div>
      </div>
    </div>
  </div>
<#-- Modal window for creating plugin -->

  <script>
function editGroupName(username, password) {
  window.alert(username + " " + password );
    //$('input#gid').val(id);
  //$('input#gname.form-control').val(name);
  }


function doUpdate(name) {
      window.location.href = "/credentials/" + encodeURIComponent(name);
      }

      function doCreate() {
      $("#createForm").ajaxForm({
      dataType: 'json',
      success: serverMessage,
      error: serverMessage
      });
      }

      // Modal windows management
      let exportInstance; // global variable
      $('#pluginsModal').on('show.bs.modal', function(event) {
      const button = $(event.relatedTarget); // Button that triggered the modal
      const modal = $(this);
      exportInstance = button.attr("name");

      const optionalBlock = modal.find('#plugin-set');
      if (exportInstance === "all") {
      optionalBlock.removeClass('hide');
      modal.find('.modal-title').text('Export all Plugins configs');
      } else {
      modal.find('#plugin-set').addClass('hide');
      modal.find('.modal-title').text(exportInstance.toUpperCase() + ' Plugin config');
      }

      modal.find('#export').click(function() {
      let format;
      if (modal.find('#json').is(":checked")) {
      format = 'json';
      }
      if (modal.find('#hocon').is(":checked")) {
      format = 'conf';
      }
      let url;
      if (exportInstance === "all") {
      let pluginGroup = "";
      if (modal.find('#all').is(":checked")) {
      pluginGroup = 'all';
      } else if (modal.find('#enabled').is(":checked")) {
      pluginGroup = 'enabled';
      } else if (modal.find('#disabled').is(":checked")) {
      pluginGroup = 'disabled';
      }
      url = '/storage/' + pluginGroup + '/plugins/export/' + format;
      } else {
      url = '/storage/' + encodeURIComponent(exportInstance) + '/export/' + format;
      }
      window.open(url);
  });
  });
  </script>
  <#include "*/alertModals.ftl">
</#macro>

<@page_html/>
