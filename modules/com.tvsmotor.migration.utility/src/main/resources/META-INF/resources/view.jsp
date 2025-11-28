<%@ include file="./init.jsp"%>

<div class="container mt-5">

	<h3 class="mb-4">Migration Utility</h3>

	<portlet:actionURL name="executeMigration" var="executeMigrationURL" />

	<form action="${executeMigrationURL}" method="post"
		enctype="multipart/form-data"
		class="p-4 border rounded shadow-sm bg-white" id="migrationUtilityFM">

		<div class="mb-4">
			<label for="<portlet:namespace/>pageType" class="form-label fw-bold">
				Select Page Type </label> <select id="<portlet:namespace/>pageType"
				name="<portlet:namespace/>pageType" class="form-select">
				<c:forEach var="page" items="${pageList}">
					<option value="${page}">${page}</option>
				</c:forEach>
			</select>
		</div>

		<div class="mb-4">
			<label for="<portlet:namespace/>jsonFile" class="form-label fw-bold">
				Upload JSON File </label> <input type="file"
				id="<portlet:namespace/>jsonFile"
				name="<portlet:namespace/>jsonFile" accept=".json"
				class="form-control" required />
		</div>

		<div class="text-center">
			<button type="submit" id="submitBtn" class="btn btn-primary px-4 py-2">
				Execute</button>
		</div>

	</form>
</div>
<liferay-ui:success key="sucess-key"  message="sucess-key" />
<liferay-ui:error embed="<%= false %>" key="unexpected-error-key" message="unexpected-error-key"/>
 

<script type="text/javascript">

$("#migrationUtilityFM").on("submit", function () {
    let btn = $("#submitBtn");
    btn.prop("disabled", true);
});

</script>


